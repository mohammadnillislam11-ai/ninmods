#ifndef NINMODS_TEXT_CODEC_H
#define NINMODS_TEXT_CODEC_H

/*
 * TEXT / HEX / OFFSET HELPERS - pure computation, no memory access here.
 * UTF-16 to UTF-8 transcoding (Unity names), hex byte parsing and offset
 * parsing. None of these touch the target process.
 */

#include <cstdint>
#include <cstring>
#include <sstream>
#include <string>
#include <vector>
#include "MemoryAccess.h"

#define halfShift 10
#define UNI_SUR_HIGH_START  (UTF32)0xD800
#define UNI_SUR_HIGH_END    (UTF32)0xDBFF
#define UNI_SUR_LOW_START   (UTF32)0xDC00
#define UNI_SUR_LOW_END     (UTF32)0xDFFF
#define UNI_REPLACEMENT_CHAR (UTF32)0x0000FFFD

typedef char UTF8;
typedef unsigned short UTF16;
typedef unsigned int UTF32;
typedef unsigned char uint8;
typedef unsigned int uint32;
static const UTF32 halfBase = 0x0010000UL;
static const UTF8 firstByteMark[7] = { 0x00, 0x00, static_cast<UTF8>(0xC0), static_cast<UTF8>(0xE0), static_cast<UTF8>(0xF0), static_cast<UTF8>(0xF8), static_cast<UTF8>(0xFC) };

typedef enum {
    strictConversion = 0,
    lenientConversion
} ConversionFlags;

typedef enum {
    conversionOK,        /* conversion successful */
    sourceExhausted,     /* partial character in source, but hit end */
    targetExhausted,     /* insuff. room in target for conversion */
    sourceIllegal,       /* source sequence is illegal/malformed */
    conversionFailed
} ConversionResult;

inline std::vector<uint8_t> ParseHexBytes(const std::string &hexString) {
    std::vector<uint8_t> bytes;
    std::istringstream stream(hexString);
    std::string byteStr;
    while (stream >> byteStr) {
        try {
            bytes.push_back(static_cast<uint8_t>(std::stoul(byteStr, nullptr, 16)));
        } catch (...) {
            /* Ignore invalid bytes */
        }
    }
    return bytes;
}

inline uintptr_t string2Offset(const char *c) {
    std::string str(c);
    if (str.empty()) return 0;
    if (str.size() > 2 && str[0] == '0' && (str[1] == 'x' || str[1] == 'X')) {
        str = str.substr(2);
    }
    uintptr_t result = 0;
    std::stringstream ss;
    ss << std::hex << str;
    ss >> result;
    return result;
}

inline int Utf16_To_Utf8(const UTF16 *sourceStart, UTF8 *targetStart, size_t outLen,
                         ConversionFlags flags) {
    int result = conversionOK;
    const UTF16 *source = sourceStart;
    UTF8 *target = targetStart;
    UTF8 *targetEnd = targetStart + outLen;

    if ((source == nullptr) || (targetStart == nullptr)) {
        return conversionFailed;
    }

    while (*source) {
        UTF32 ch;
        unsigned short bytesToWrite = 0;
        const UTF32 byteMask = 0xBF;
        const UTF32 byteMark = 0x80;
        const UTF16 *oldSource = source;

        ch = *source++;

        if (ch >= UNI_SUR_HIGH_START && ch <= UNI_SUR_HIGH_END) {
            if (*source) {
                UTF32 ch2 = *source;
                if (ch2 >= UNI_SUR_LOW_START && ch2 <= UNI_SUR_LOW_END) {
                    ch = ((ch - UNI_SUR_HIGH_START) << halfShift) + (ch2 - UNI_SUR_LOW_START) + halfBase;
                    ++source;
                } else if (flags == strictConversion) {
                    --source;
                    result = sourceIllegal;
                    break;
                }
            } else {
                --source;
                result = sourceExhausted;
                break;
            }
        } else if (flags == strictConversion) {
            if (ch >= UNI_SUR_LOW_START && ch <= UNI_SUR_LOW_END) {
                --source;
                result = sourceIllegal;
                break;
            }
        }

        if (ch < (UTF32)0x80) {
            bytesToWrite = 1;
        } else if (ch < (UTF32)0x800) {
            bytesToWrite = 2;
        } else if (ch < (UTF32)0x10000) {
            bytesToWrite = 3;
        } else if (ch < (UTF32)0x110000) {
            bytesToWrite = 4;
        } else {
            bytesToWrite = 3;
            ch = UNI_REPLACEMENT_CHAR;
        }

        target += bytesToWrite;
        if (target > targetEnd) {
            source = oldSource;
            target -= bytesToWrite;
            result = targetExhausted;
            break;
        }

        switch (bytesToWrite) {
            case 4:
                *--target = (UTF8)((ch | byteMark) & byteMask);
                ch >>= 6;
                [[fallthrough]];
            case 3:
                *--target = (UTF8)((ch | byteMark) & byteMask);
                ch >>= 6;
                [[fallthrough]];
            case 2:
                *--target = (UTF8)((ch | byteMark) & byteMask);
                ch >>= 6;
                [[fallthrough]];
            case 1:
                *--target = (UTF8)(ch | firstByteMark[bytesToWrite]);
        }
        target += bytesToWrite;
    }

    /* Null terminate if there is space */
    if (target < targetEnd) {
        *target = '\0';
    } else if (target == targetEnd) {
        *(target - 1) = '\0';
    }

    return result;
}

inline void getCharacterName(uintptr_t address, UTF8 *transcoding, size_t bufSize) {
    if (bufSize < 1) return;

    memset(transcoding, 0, bufSize);

    UTF16 buf16[34] = {0};
    int m = 0;

    for (int i = 0; i < 4; i++) {
        int classname = Read<int>(address + i * 4);
        int high = (classname >> 16) & 0xFFFF;
        int low = classname & 0xFFFF;

        if (low) buf16[m++] = static_cast<UTF16>(low);
        if (high) buf16[m++] = static_cast<UTF16>(high);
        if (m >= 32) break;
    }

    buf16[m] = 0;

    int result = Utf16_To_Utf8(buf16, transcoding, bufSize - 1, strictConversion);
    if (result != conversionOK) {
        transcoding[0] = '\0';
    }
}

#endif  // NINMODS_TEXT_CODEC_H
