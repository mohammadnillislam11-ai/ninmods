#ifndef UTF_HELPERS_H
#define UTF_HELPERS_H

#include <string>
#include <cstdint>
#include <cstdlib>

static inline uint16_t byteswap_ushort(uint16_t number) {
#if defined(_MSC_VER) && _MSC_VER > 1310
    return _byteswap_ushort(number);
#elif defined(__GNUC__)
    return __builtin_bswap16(number);
#else
    return (number >> 8) | (number << 8);
#endif
}

inline std::string utf16le_to_utf8(const std::u16string &u16str) {
    if (u16str.empty()) return std::string();
    const char16_t *p = u16str.data();
    std::u16string::size_type len = u16str.length();
    if (p[0] == 0xFEFF) { p += 1; len -= 1; }
    std::string u8str;
    u8str.reserve(len * 3);
    char16_t u16;
    for (std::u16string::size_type i = 0; i < len; ++i) {
        u16 = p[i];
        if (u16 < 0x0080) {
            u8str.push_back((char) u16);
        } else if (u16 < 0x0800) {
            u8str.push_back((char) (0xC0 | (u16 >> 6)));
            u8str.push_back((char) (0x80 | (u16 & 0x3F)));
        } else if (u16 >= 0xD800 && u16 <= 0xDBFF) {
            if (i + 1 < len) {
                char16_t next = p[i + 1];
                if (next >= 0xDC00 && next <= 0xDFFF) {
                    uint32_t code_point = ((((uint32_t) u16 - 0xD800) << 10) | ((uint32_t) next - 0xDC00)) + 0x10000;
                    u8str.push_back((char) (0xF0 | (code_point >> 18)));
                    u8str.push_back((char) (0x80 | ((code_point >> 12) & 0x3F)));
                    u8str.push_back((char) (0x80 | ((code_point >> 6) & 0x3F)));
                    u8str.push_back((char) (0x80 | (code_point & 0x3F)));
                    ++i;
                }
            }
        } else {
            u8str.push_back((char) (0xE0 | (u16 >> 12)));
            u8str.push_back((char) (0x80 | ((u16 >> 6) & 0x3F)));
            u8str.push_back((char) (0x80 | (u16 & 0x3F)));
        }
    }
    return u8str;
}

inline std::u16string utf8_to_utf16le(const std::string &u8str, bool addbom = false) {
    std::u16string u16str;
    u16str.reserve(u8str.size());
    if (addbom) u16str.push_back(0xFEFF);
    std::string::size_type len = u8str.length();
    const unsigned char *p = (const unsigned char *) u8str.data();
    for (std::string::size_type i = 0; i < len;) {
        char16_t u16;
        if (p[i] < 0x80) {
            u16 = p[i];
            i += 1;
        } else if (p[i] < 0xE0) {
            u16 = ((p[i] & 0x1F) << 6) | (p[i + 1] & 0x3F);
            i += 2;
        } else if (p[i] < 0xF0) {
            u16 = ((p[i] & 0x0F) << 12) | ((p[i + 1] & 0x3F) << 6) | (p[i + 2] & 0x3F);
            i += 3;
        } else {
            uint32_t code_point = ((p[i] & 0x07) << 18) | ((p[i + 1] & 0x3F) << 12) | ((p[i + 2] & 0x3F) << 6) | (p[i + 3] & 0x3F);
            code_point -= 0x10000;
            u16str.push_back((char16_t) (0xD800 | (code_point >> 10)));
            u16str.push_back((char16_t) (0xDC00 | (code_point & 0x3FF)));
            i += 4;
            continue;
        }
        u16str.push_back(u16);
    }
    return u16str;
}

#endif // UTF_HELPERS_H
