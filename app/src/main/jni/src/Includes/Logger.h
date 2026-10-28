#pragma once
#include <string>
#include <fstream>
#include <mutex>
#include <chrono>
#include <ctime>
#include <cstdarg>
#include <cstdio>
#include <sys/stat.h>
#include <unistd.h>
#include <android/log.h>

/**
 * NINMODS NATIVE LOGGER
 * ---------------------
 * Strictly saves to /storage/emulated/0/Download/ninmods/ninmods.log only.
 * Flushes on every line so that diagnostic data is immediately readable in
 * the file manager and never remains 0 bytes.
 */
class NinLogger {
public:
    static void LogToLogcat(const char* tag, const char* format, ...) {
        char buffer[2048];
        va_list args;
        va_start(args, format);
        vsnprintf(buffer, sizeof(buffer), format, args);
        va_end(args);
        __android_log_print(ANDROID_LOG_INFO, tag, "%s", buffer);
    }

    static void Log(const char* tag, const char* format, ...) {
        char buffer[2048];
        va_list args;
        va_start(args, format);
        vsnprintf(buffer, sizeof(buffer), format, args);
        va_end(args);

        __android_log_print(ANDROID_LOG_INFO, tag, "%s", buffer);

        static std::mutex s_mutex;
        std::lock_guard<std::mutex> lock(s_mutex);

        static FILE* s_file = nullptr;

        if (s_file == nullptr) {
            mkdir("/storage/emulated/0/Download", 0777);
            mkdir("/storage/emulated/0/Download/ninmods", 0777);
            s_file = fopen("/storage/emulated/0/Download/ninmods/ninmods.log", "a");
            if (s_file != nullptr) {
                chmod("/storage/emulated/0/Download/ninmods/ninmods.log", 0666);
            }
        }
        if (s_file == nullptr) {
            return;
        }

        auto now = std::chrono::system_clock::now();
        auto in_time_t = std::chrono::system_clock::to_time_t(now);
        auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()) % 1000;

        char timeStr[64];
        struct tm tm_buf;
        localtime_r(&in_time_t, &tm_buf);
        snprintf(timeStr, sizeof(timeStr), "%04d-%02d-%02d %02d:%02d:%02d.%03d",
                 tm_buf.tm_year + 1900, tm_buf.tm_mon + 1, tm_buf.tm_mday,
                 tm_buf.tm_hour, tm_buf.tm_min, tm_buf.tm_sec, (int)ms.count());

        fprintf(s_file, "[%s] [%s] %s\n", timeStr, tag, buffer);
        fflush(s_file);

        if (ferror(s_file)) {
            fclose(s_file);
            s_file = nullptr;
        }
    }
};

#define NIN_LOGI(tag, fmt, ...) NinLogger::Log(tag, fmt, ##__VA_ARGS__)
#define NIN_LOGD(tag, fmt, ...) NinLogger::LogToLogcat(tag, "[DEBUG] " fmt, ##__VA_ARGS__)
#define NIN_LOGE(tag, fmt, ...) NinLogger::Log(tag, "[ERROR] " fmt, ##__VA_ARGS__)
