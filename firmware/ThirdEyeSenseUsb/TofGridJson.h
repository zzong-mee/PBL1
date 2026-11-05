#pragma once
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdarg.h>

// Bounded JSON writer: never transmit a truncated grid or remove invalid zone positions.
class TofGridJson {
 public:
  TofGridJson(char *buffer, size_t capacity): buffer_(buffer), capacity_(capacity) { if (capacity) buffer[0]=0; }
  void append(const char *format, ...) {
    if (!ok_) return;
    va_list args; va_start(args,format);
    const int written=vsnprintf(buffer_+used_,capacity_-used_,format,args);
    va_end(args);
    if (written<0 || size_t(written)>=capacity_-used_) { ok_=false; return; }
    used_+=size_t(written);
  }
  template<class Result>
  void grid(const char *key, const Result &result, bool fresh, size_t stride) {
    append(",\"%s_zones_mm\":[",key);
    for (size_t zone=0;zone<64;++zone) {
      const auto i=zone*stride;
      const int distance=result.distance_mm[i];
      const auto status=result.target_status[i];
      if (zone) append(",");
      if (fresh && result.nb_target_detected[zone]>0 && (status==5 || status==6 || status==9)
          && distance>0 && distance<=4000) append("%d",distance);
      else append("null");
    }
    append("],\"%s_status\":[",key);
    for (size_t zone=0;zone<64;++zone) {
      if (zone) append(",");
      if (fresh) append("%u",unsigned(result.target_status[zone*stride]));
      else append("null");
    }
    append("]");
  }
  bool ok() const { return ok_; }
 private:
  char *buffer_; size_t capacity_,used_=0; bool ok_=true;
};
