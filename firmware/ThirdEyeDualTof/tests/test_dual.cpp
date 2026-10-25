// Host simulation: verifies control flow, not physical I2C or ESP32 execution.
#include "../ThirdEyeDualTof.ino"
#include <iostream>
int main(int argc,char **argv) {
 assert(argc==2);
 std::string scenario=argv[1];
 if(scenario=="warm") { addresses[D2]=ADDRESS_A; addresses[D3]=ADDRESS_B; }
 if(scenario=="swapped") { addresses[D2]=ADDRESS_B; addresses[D3]=ADDRESS_A; }
 if(scenario=="missingB") present[D3]=false;
 if(scenario=="initError") faultPin=D2;
 setup();
 if(scenario=="missingB" || scenario=="initError") {
  assert(failed && !pins[D2] && !pins[D3]);
  assert(Serial.out.find("READY:")==std::string::npos);
 } else {
  assert(!failed && pins[D2] && pins[D3]);
  assert(addresses[D2]==ADDRESS_A && addresses[D3]==ADDRESS_B);
  if(scenario=="timeout") { dataReady=false; delay(3001); loop(); assert(failed && !pins[D2] && !pins[D3]); }
  else if(scenario=="readError") { faultPin=D3; loop(); assert(failed && !pins[D2] && !pins[D3]); }
  else {
   delay(500); loop();
   assert(Serial.out.find("Sensor A Distance [mm]")!=std::string::npos);
   assert(Serial.out.find("Sensor B Distance [mm]")!=std::string::npos);
   Serial.command='r'; loop(); assert(resolution==64 && sensorA.res==64 && sensorB.res==64);
   Serial.command='r'; loop(); assert(resolution==16 && sensorA.res==16 && sensorB.res==16);
   Serial.out.clear(); resultsA.target_status[0]=0;
   printGrid("A",resultsA); assert(Serial.out.find("\n-\t")!=std::string::npos);
  }
 }
 std::cout<<scenario<<": PASS\n";
}
