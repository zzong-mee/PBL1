#include "../TofGridJson.h"
#include <cassert>
#include <cstring>
#include <cstdio>
struct Result { int distance_mm[128]={}; unsigned target_status[128]={}; unsigned nb_target_detected[64]={}; };
int main() {
 Result r;
 for(int i=0;i<64;++i) { r.distance_mm[i*2]=1000+i; r.target_status[i*2]=5; r.nb_target_detected[i]=1; }
 r.target_status[0]=0;
 char json[4096]; TofGridJson w(json,sizeof(json));
 w.append("{\"grid_size\":8,\"grid_order\":\"st_uld_raw\",\"left_age_ms\":0,\"right_age_ms\":0,\"tof_error\":\"\"");w.grid("left",r,true,2);w.grid("right",r,false,2);w.append("}");
 assert(w.ok());assert(strstr(json,"[null,1001,1002"));assert(strstr(json,"1063]"));assert(strstr(json,"\"right_zones_mm\":[null,null"));
 char shortBuffer[12];TofGridJson shortWriter(shortBuffer,sizeof(shortBuffer));shortWriter.grid("left",r,true,2);assert(!shortWriter.ok());
 std::puts(json);
}
