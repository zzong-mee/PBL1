package com.guideeye.app.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.guideeye.app.R
import com.guideeye.app.data.DistanceReading
import java.util.concurrent.Executors

/** Foreground-only, single CDC ACM port. Never guesses which of multiple devices to open. */
class UsbDistanceController(
    private val context: Context,
    private val status: (Int) -> Unit,
    private val reading: (DistanceReading) -> Unit
) {
    private val manager = context.getSystemService(UsbManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val permissionAction = "${context.packageName}.USB_PERMISSION"
    private var registered = false
    @Volatile private var generation = 0
    @Volatile private var connection: UsbDeviceConnection? = null
    private var selected: UsbDevice? = null

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action != permissionAction || intent.getIntExtra("generation", -1) != generation) return
            val device = deviceExtra(intent) ?: return
            if (device.deviceId != selected?.deviceId) return
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && manager.hasPermission(device)) {
                open(device)
            } else { selected = null; status(R.string.usb_denied) }
        }
    }
    private val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val device = deviceExtra(intent) ?: return
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> if (device.deviceId == selected?.deviceId) {
                    disconnect(); status(R.string.usb_detached)
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> if (selected == null) status(R.string.usb_attached)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun deviceExtra(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

    fun start() {
        if (registered) return
        val filter = IntentFilter(permissionAction)
        val deviceFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, permissionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(context, deviceReceiver, deviceFilter, ContextCompat.RECEIVER_EXPORTED)
        registered = true
    }

    private fun supported(device: UsbDevice): Boolean {
        // Espressif native USB only; composite/multiport layouts require explicit pairing support.
        if (device.vendorId != 0x303a) return false
        val interfaces = (0 until device.interfaceCount).map(device::getInterface)
        return interfaces.count { it.interfaceClass == UsbConstants.USB_CLASS_COMM && it.interfaceSubclass == 2 } == 1 &&
            interfaces.count { it.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA } == 1
    }

    fun connect() {
        disconnect()
        try {
            val devices = manager.deviceList.values.toList()
            val candidates = devices.filter(::supported)
            if (candidates.size != 1) {
                status(when {
                    candidates.size > 1 -> R.string.usb_multiple
                    devices.isEmpty() -> R.string.usb_missing
                    else -> R.string.usb_unsupported
                })
                return
            }
            val device = candidates.single()
            selected = device
            if (manager.hasPermission(device)) open(device)
            else {
                status(R.string.usb_permission)
                val pending = PendingIntent.getBroadcast(context, device.deviceId,
                    Intent(permissionAction).setPackage(context.packageName).putExtra("generation", generation),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                manager.requestPermission(device, pending)
            }
        } catch (_: Exception) { disconnect(); status(R.string.usb_error) }
    }

    private fun open(device: UsbDevice) {
        val token = generation
        status(R.string.usb_opening)
        worker.execute {
            if (token != generation) return@execute
            var local: UsbDeviceConnection? = null
            val claimed = mutableListOf<UsbInterface>()
            try {
                local = manager.openDevice(device) ?: error("Open failed")
                val interfaces = (0 until device.interfaceCount).map(device::getInterface)
                val control = interfaces.single { it.interfaceClass == UsbConstants.USB_CLASS_COMM && it.interfaceSubclass == 2 }
                val data = interfaces.single { it.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA }
                val input = (0 until data.endpointCount).map(data::getEndpoint).single {
                    it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN
                }
                for (iface in listOf(control, data)) {
                    check(local.claimInterface(iface, true)); claimed.add(iface)
                }
                // CDC SET_LINE_CODING: 115200 baud, 8 data bits, no parity, 1 stop bit.
                val coding = byteArrayOf(0x00, 0xc2.toByte(), 0x01, 0x00, 0x00, 0x00, 0x08)
                check(local.controlTransfer(0x21, 0x20, 0, control.id, coding, coding.size, 1000) == coding.size)
                check(local.controlTransfer(0x21, 0x22, 3, control.id, null, 0, 1000) >= 0)
                synchronized(this) {
                    if (token != generation) return@execute
                    connection = local
                }
                emit(token) { status(R.string.usb_waiting) }
                val decoder = DistanceLineDecoder()
                val buffer = ByteArray(4096)
                var lastValid = SystemClock.elapsedRealtime()
                while (token == generation) {
                    val size = local.bulkTransfer(input, buffer, buffer.size, 250)
                    if (size > 0) {
                        val samples = decoder.accept(buffer, size)
                        if (samples.isNotEmpty()) {
                            lastValid = SystemClock.elapsedRealtime()
                            val newest = samples.last()
                            emit(token) { status(R.string.usb_receiving); reading(newest) }
                        }
                    }
                    if (SystemClock.elapsedRealtime() - lastValid > 4000) {
                        emit(token) { disconnect(); status(R.string.usb_timeout) }
                        break
                    }
                }
            } catch (_: Exception) {
                emit(token) { disconnect(); status(R.string.usb_error) }
            } finally {
                for (iface in claimed.asReversed()) runCatching { local?.releaseInterface(iface) }
                local?.close()
                synchronized(this) { if (connection === local) connection = null }
            }
        }
    }

    private fun emit(token: Int, action: () -> Unit) {
        main.post { if (token == generation && registered) action() }
    }

    fun disconnect() {
        synchronized(this) {
            generation++
            connection?.close()
            connection = null
        }
        selected = null
    }

    fun stop() {
        disconnect()
        if (registered) {
            context.unregisterReceiver(permissionReceiver)
            context.unregisterReceiver(deviceReceiver)
            registered = false
        }
        main.removeCallbacksAndMessages(null)
    }
    fun close() { stop(); worker.shutdownNow() }
}
