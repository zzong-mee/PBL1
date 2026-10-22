/**
******************************************************************************
* @file    VL53L8CX_HelloWorld_I2C.ino
* @author  STMicroelectronics
* @version V2.0.0
* @date    27 June 2024
* @brief   Arduino test application for STMicroelectronics VL53L8CX
*          proximity sensor satellite based on FlightSense.
*          This application makes use of C++ classes obtained from the C
*          components' drivers.
******************************************************************************
* @attention
*
* <h2><center>&copy; COPYRIGHT(c) 2024 STMicroelectronics</center></h2>
*
* Redistribution and use in source and binary forms, with or without modification,
* are permitted provided that the following conditions are met:
*   1. Redistributions of source code must retain the above copyright notice,
*      this list of conditions and the following disclaimer.
*   2. Redistributions in binary form must reproduce the above copyright notice,
*      this list of conditions and the following disclaimer in the documentation
*      and/or other materials provided with the distribution.
*   3. Neither the name of STMicroelectronics nor the names of its contributors
*      may be used to endorse or promote products derived from this software
*      without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
* FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
* DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
* SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
* CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
******************************************************************************
*/
/*
 * To use these examples you need to connect the VL53L8CX satellite sensor directly to the Nucleo board with wires as explained below:
 * pin 1 (SPI_I2C_N) of the VL53L8CX satellite connected to pin GND of the Nucleo board
 * pin 2 (LPn) of the VL53L8CX satellite connected to pin A3 of the Nucleo board
 * pin 3 (NCS) not connected
 * pin 4 (MISO) not connected
 * pin 5 (MOSI_SDA) of the VL53L8CX satellite connected to pin D14 (SDA) of the Nucleo board
 * pin 6 (MCLK_SCL) of the VL53L8CX satellite connected to pin D15 (SCL) of the Nucleo board
 * pin 7 (PWREN) of the VL53L8CX satellite connected to pin D11 of the Nucleo board
 * pin 8 (I0VDD) of the VL53L8CX satellite not connected
 * pin 9 (3V3) of the VL53L8CX satellite not connected
 * pin 10 (1V8) of the VL53L8CX satellite not connected
 * pin 11 (5V) of the VL53L8CX satellite connected to 5V of the Nucleo board
 * GPIO1 of VL53L8CX satellite connected to A2 pin of the Nucleo board (not used)
 * GND of the VL53L8CX satellite connected to GND of the Nucleo board
 */

/* Includes ------------------------------------------------------------------*/

#include <vl53l8cx.h>


#ifdef ARDUINO_SAM_DUE
  #define DEV_I2C Wire1
#else
  #define DEV_I2C Wire
#endif
#define SerialPort Serial

#define LP_A D2
#define LP_B D3
#define LPN_PIN LP_A
#define PWREN_PIN -1

void print_result(VL53L8CX_ResultsData *Result);
void clear_screen(void);
void handle_cmd(uint8_t cmd);
void display_commands_banner(void);

// Components.
VL53L8CX sensor_vl53l8cx_top(&DEV_I2C, LPN_PIN);

bool EnableAmbient = false;
bool EnableSignal = false;
uint8_t res = VL53L8CX_RESOLUTION_4X4;
char report[256];
uint8_t status;

/* Setup ---------------------------------------------------------------------*/
void setup()
{

  pinMode(LP_A, OUTPUT);
  pinMode(LP_B, OUTPUT);
  digitalWrite(LP_A, LOW);
  digitalWrite(LP_B, LOW);
  delay(20);
  digitalWrite(LP_A, HIGH);
  delay(20);

  // Enable PWREN pin if present
  if (PWREN_PIN >= 0) {
    pinMode(PWREN_PIN, OUTPUT);
    digitalWrite(PWREN_PIN, HIGH);
    delay(10);
  }

  // Initialize serial for output.
  SerialPort.begin(115200);

  // Initialize I2C bus.
  DEV_I2C.begin(D4, D5);
  DEV_I2C.setClock(400000);

  // Configure VL53L8CX component.
  sensor_vl53l8cx_top.begin();
  status = sensor_vl53l8cx_top.init();

  // Start Measurements
  status = sensor_vl53l8cx_top.start_ranging();
}

void loop()
{
  VL53L8CX_ResultsData Results;
  uint8_t NewDataReady = 0;

  do {
    status = sensor_vl53l8cx_top.check_data_ready(&NewDataReady);
  } while (!NewDataReady);

  if ((!status) && (NewDataReady != 0)) {
    status = sensor_vl53l8cx_top.get_ranging_data(&Results);
    print_result(&Results);
  }

  if (Serial.available() > 0) {
    handle_cmd(Serial.read());
  }
  delay(100);
}

void print_result(VL53L8CX_ResultsData *Result)
{
  const uint8_t side = (res == VL53L8CX_RESOLUTION_8X8) ? 8 : 4;
  clear_screen();
  SerialPort.println("Distance [mm]");
  SerialPort.println("-------------");
  for (uint8_t row = 0; row < side; row++) {
    for (uint8_t col = 0; col < side; col++) {
      const uint8_t zone = row * side + col;
      const uint8_t index = VL53L8CX_NB_TARGET_PER_ZONE * zone;
      const int32_t distance = Result->distance_mm[index];
      const bool valid = Result->nb_target_detected[zone] > 0 && distance > 0 && distance <= 4000;
      if (valid) {
        char cell[8];
        snprintf(cell, sizeof(cell), "%4ld", (long)distance);
        SerialPort.print(cell);
      } else {
        SerialPort.print("   -");
      }
      if (col < side - 1) SerialPort.print(" | ");
    }
    SerialPort.println();
  }
  SerialPort.println();
  SerialPort.println("r: 4x4/8x8  s: signal  c: clear");
}

void toggle_resolution(void)
{
  status = sensor_vl53l8cx_top.stop_ranging();

  switch (res) {
    case VL53L8CX_RESOLUTION_4X4:
      res = VL53L8CX_RESOLUTION_8X8;
      break;

    case VL53L8CX_RESOLUTION_8X8:
      res = VL53L8CX_RESOLUTION_4X4;
      break;

    default:
      break;
  }
  status = sensor_vl53l8cx_top.set_resolution(res);
  status = sensor_vl53l8cx_top.start_ranging();
}

void toggle_signal_and_ambient(void)
{
  EnableAmbient = (EnableAmbient) ? false : true;
  EnableSignal = (EnableSignal) ? false : true;
}

void clear_screen(void)
{
  snprintf(report, sizeof(report), "%c[2J", 27); /* 27 is ESC command */
  SerialPort.print(report);
}

void display_commands_banner(void)
{
  snprintf(report, sizeof(report), "%c[2H", 27); /* 27 is ESC command */
  SerialPort.print(report);

  Serial.print("53L8A1 Simple Ranging demo application\n");
  Serial.print("--------------------------------------\n\n");

  Serial.print("Use the following keys to control application\n");
  Serial.print(" 'r' : change resolution\n");
  Serial.print(" 's' : enable signal and ambient\n");
  Serial.print(" 'c' : clear screen\n");
  Serial.print("\n");
}

void handle_cmd(uint8_t cmd)
{
  switch (cmd) {
    case 'r':
      toggle_resolution();
      clear_screen();
      break;

    case 's':
      toggle_signal_and_ambient();
      clear_screen();
      break;

    case 'c':
      clear_screen();
      break;

    default:
      break;
  }
}
