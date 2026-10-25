// Export the APK's actual pure Kotlin renderer output as stereo PCM WAV.
// Build the app first; compile/run with app/build/tmp/kotlin-classes/debug and kotlin-stdlib on classpath.
import com.guideeye.app.audio.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

public final class ExportHrtfPreview {
    public static void main(String[] args) throws Exception {
        HrirBank bank = HrirBank.Companion.parse(Files.readAllBytes(Path.of(args[0])));
        Map<Integer, short[]> rendered = HrtfRenderer.INSTANCE.render(bank);
        DistanceBeepPolicy.Signal signal = DistanceBeepPolicy.INSTANCE.signal(2000, null, 0);
        int rate = bank.getRate();
        int segmentFrames = rate * 1800 / 1000;
        short[] output = new short[segmentFrames * 8 * 2];
        for (int direction=0;direction<8;direction++) {
            short[] pulse = rendered.get(direction * 45);
            for (long offsetMs=0; offsetMs<1700; offsetMs+=signal.getIntervalMs()) {
                int offset = direction * segmentFrames * 2 + (int)(offsetMs * rate / 1000) * 2;
                for (int i=0;i<pulse.length && offset+i<(direction+1)*segmentFrames*2;i++)
                    output[offset+i] = (short)(pulse[i] * signal.getGain());
            }
        }
        int size = output.length * 2;
        ByteBuffer wav = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes()).putInt(36+size).put("WAVEfmt ".getBytes()).putInt(16);
        wav.putShort((short)1).putShort((short)2).putInt(rate).putInt(rate*4).putShort((short)4).putShort((short)16);
        wav.put("data".getBytes()).putInt(size);
        for (short value : output) wav.putShort(value);
        Files.write(Path.of(args[1]),wav.array());
    }
}
