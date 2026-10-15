// XIAO ESP32-S3 Sense, ESP32 Arduino 3.x. USB CDC On Boot Enabled, PSRAM OPI.
// Camera-only integration firmware. ToF pin/voltage confirmation is still required.
#include <Arduino.h>
#include "esp_camera.h"

uint32_t sequence = 0;
bool cameraReady = false;
uint32_t crcUpdate(uint32_t crc, const uint8_t* data, size_t size) {
  for (size_t i=0;i<size;i++) {
    crc ^= data[i];
    for (int bit=0;bit<8;bit++) crc=(crc>>1)^((crc&1)?0xedb88320UL:0);
  }
  return crc;
}
void put32(uint8_t* p, uint32_t value) { for(int i=0;i<4;i++) p[i]=value>>(8*i); }
bool writeAll(const uint8_t* bytes, size_t size) {
  uint32_t lastProgress=millis();
  while(size && Serial) {
    int room=Serial.availableForWrite();
    if(room>0) {
      size_t n=Serial.write(bytes,min(size,(size_t)room));
      if(n) { bytes+=n;size-=n;lastProgress=millis(); }
    } else delay(1);
    if(millis()-lastProgress>1000) return false;
  }
  return size==0;
}
bool packet(uint8_t type,const uint8_t* data,size_t size) {
  if(!Serial || size>262144 || size==0) return false;
  uint8_t header[14]={1,type};put32(header+2,size);put32(header+6,sequence++);put32(header+10,millis());
  uint32_t crc=crcUpdate(crcUpdate(0xffffffffUL,header,14),data,size)^0xffffffffUL;
  uint8_t check[4];put32(check,crc);
  return writeAll((const uint8_t*)"GEYE",4) && writeAll(header,14) && writeAll(data,size) && writeAll(check,4);
}
void setup() {
  Serial.begin(115200);
  camera_config_t c={};
  c.ledc_channel=LEDC_CHANNEL_0;c.ledc_timer=LEDC_TIMER_0;
  c.pin_d0=15;c.pin_d1=17;c.pin_d2=18;c.pin_d3=16;
  c.pin_d4=14;c.pin_d5=12;c.pin_d6=11;c.pin_d7=48;
  c.pin_xclk=10;c.pin_pclk=13;c.pin_vsync=38;c.pin_href=47;
  c.pin_sccb_sda=40;c.pin_sccb_scl=39;c.pin_pwdn=-1;c.pin_reset=-1;
  c.xclk_freq_hz=20000000;c.pixel_format=PIXFORMAT_JPEG;
  c.frame_size=FRAMESIZE_QVGA;c.jpeg_quality=15;c.fb_count=1;
  c.fb_location=psramFound()?CAMERA_FB_IN_PSRAM:CAMERA_FB_IN_DRAM;
  c.grab_mode=CAMERA_GRAB_WHEN_EMPTY;
  cameraReady=esp_camera_init(&c)==ESP_OK;
}
void loop() {
  if(!Serial) { delay(50);return; }
  // Missing sensors are null, never simulated as a physical measurement.
  const char* distances="{\"left_mm\":null,\"right_mm\":null}";
  packet(1,(const uint8_t*)distances,strlen(distances));
  if(cameraReady) {
    camera_fb_t* frame=esp_camera_fb_get();
    if(frame) {
      if(frame->format==PIXFORMAT_JPEG) packet(2,frame->buf,frame->len);
      esp_camera_fb_return(frame);
    }
  } else {
    const char* error="{\"camera\":\"initialization_failed\"}";
    packet(3,(const uint8_t*)error,strlen(error));
  }
  delay(500);
}
