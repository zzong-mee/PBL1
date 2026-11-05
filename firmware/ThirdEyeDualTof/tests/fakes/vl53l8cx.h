#pragma once
#include "Wire.h"
constexpr uint8_t VL53L8CX_RESOLUTION_4X4=16,VL53L8CX_RESOLUTION_8X8=64;
constexpr int VL53L8CX_NB_TARGET_PER_ZONE=1;
struct VL53L8CX_ResultsData { int16_t distance_mm[64]={}; uint8_t target_status[64]={}; uint8_t nb_target_detected[64]={}; };
struct VL53L8CX {
 int pin; uint8_t address=0x29,res=16; bool ranging=false;
 VL53L8CX(TwoWire *,int p):pin(p) {}
 int begin(uint16_t a=0x52) { digitalWrite(pin,LOW); delay(10); digitalWrite(pin,HIGH); address=a>>1; return 0; }
 int is_alive(uint8_t *a) { assert(address==addresses[pin]); *a=1; return 0; }
 int init() { return pin==faultPin ? 7 : 0; }
 int set_i2c_address(uint16_t a) { assert(address==addresses[pin]); addresses[pin]=address=a>>1; return 0; }
 int set_resolution(uint8_t r) { res=r; return 0; }
 int set_ranging_frequency_hz(int) { return 0; }
 void off() { digitalWrite(pin,LOW); }
 void on() { digitalWrite(pin,HIGH); }
 int start_ranging() { ranging=true; return 0; }
 int stop_ranging() { ranging=false; return 0; }
 int check_data_ready(uint8_t *r) { *r=dataReady; return pin==faultPin ? 7 : 0; }
 int get_ranging_data(VL53L8CX_ResultsData *r) {
  assert(ranging);
  for(int i=0;i<res;++i) { r->distance_mm[i]=pin==D2 ? 300 : 800; r->target_status[i]=5; r->nb_target_detected[i]=1; }
  return 0;
 }
};
