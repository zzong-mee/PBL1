#pragma once
#include <stddef.h>
#include <stdint.h>

namespace Geye {
inline uint32_t crcUpdate(uint32_t crc, const uint8_t *data, size_t size) {
  for (size_t i = 0; i < size; ++i) {
    crc ^= data[i];
    for (int bit = 0; bit < 8; ++bit) crc = (crc >> 1) ^ ((crc & 1) ? 0xedb88320UL : 0);
  }
  return crc;
}
inline void put32(uint8_t *p, uint32_t value) {
  for (int i = 0; i < 4; ++i) p[i] = uint8_t(value >> (8 * i));
}
template<class Writer>
bool packet(uint8_t type, const uint8_t *data, size_t size, uint32_t sequence,
            uint32_t timestamp, Writer write) {
  const size_t limit = type == 1 ? 4096 : type == 2 ? 262144 : type == 3 ? 1024 : 0;
  if (!data || size == 0 || size > limit) return false;
  uint8_t header[14] = {1, type};
  put32(header + 2, uint32_t(size)); put32(header + 6, sequence); put32(header + 10, timestamp);
  uint8_t check[4];
  put32(check, crcUpdate(crcUpdate(0xffffffffUL, header, sizeof(header)), data, size) ^ 0xffffffffUL);
  const uint8_t magic[] = {'G', 'E', 'Y', 'E'};
  return write(magic, sizeof(magic)) && write(header, sizeof(header)) &&
         write(data, size) && write(check, sizeof(check));
}
}
