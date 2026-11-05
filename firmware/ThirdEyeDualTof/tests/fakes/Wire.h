#pragma once
#include <cstdint>
#include <string>
#include <cassert>
constexpr int D2=2,D3=3,D4=4,D5=5,OUTPUT=1,LOW=0,HIGH=1,HEX=16;
inline uint32_t now=0;
inline int pins[8]={};
inline uint8_t addresses[8]={0,0,0x29,0x29};
inline bool present[8]={false,false,true,true};
inline int faultPin=-1;
inline bool dataReady=true;
inline void pinMode(int,int) {}
inline void digitalWrite(int p,int v) { pins[p]=v; }
inline void delay(uint32_t ms) { now+=ms; }
inline uint32_t millis() { return now; }
struct SerialFake {
 std::string out;
 char command=0;
 void begin(int) {}
 explicit operator bool() const { return true; }
 int available() { return command!=0; }
 int read() { char c=command; command=0; return c; }
 void print(const char *s) { out+=s; }
 void print(char c) { out+=c; }
 template<class T> void print(T v) { out+=std::to_string(v); }
 template<class T> void println(T v) { print(v); out+='\n'; }
 void println(int v,int) { println(v); }
};
inline SerialFake Serial;
struct TwoWire {
 uint8_t selected=0;
 void begin(int,int) {}
 void setClock(int) {}
 void setTimeOut(int) {}
 void beginTransmission(uint8_t a) { selected=a; }
 int endTransmission() {
  int count=0;
  for (int p : {D2,D3}) if(present[p] && pins[p] && addresses[p]==selected) ++count;
  assert(count<=1); // Never allow an address collision during a transaction.
  return count==1 ? 0 : 2;
 }
};
inline TwoWire Wire;
