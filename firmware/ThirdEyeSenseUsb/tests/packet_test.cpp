#include "../GeyePacket.h"
#include <cassert>
#include <cstdio>
#include <vector>
int main() {
  // Fixture includes magic and JSON-like text inside JPEG, which must stay binary.
  const uint8_t jpeg[] = {0xff,0xd8,'G','E','Y','E','{','}',0xff,0xd9};
  std::vector<uint8_t> bytes;
  auto writer = [&](const uint8_t *p, size_t n) { bytes.insert(bytes.end(),p,p+n); return true; };
  assert(Geye::packet(2,jpeg,sizeof(jpeg),7,1234,writer));
  for (uint8_t b : bytes) std::printf("%02x",b);
  std::puts("");
  bytes.clear();
  assert(!Geye::packet(2,jpeg,262145,0,0,writer));
  assert(!Geye::packet(1,jpeg,4097,0,0,writer));
  assert(!Geye::packet(3,jpeg,1025,0,0,writer));
  assert(!Geye::packet(4,jpeg,sizeof(jpeg),0,0,writer));
  assert(!Geye::packet(2,jpeg,0,0,0,writer));
  assert(bytes.empty());
  int writes=0;
  auto fail = [&](const uint8_t *, size_t) { return ++writes < 3; };
  assert(!Geye::packet(2,jpeg,sizeof(jpeg),0,0,fail));
  assert(writes==3);
}
