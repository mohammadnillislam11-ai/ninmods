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

class NinLogger {
public:
    static void Log(const char* tag, const char* format, ...) {
        char buffer[2048];
        va_list args;
        va_start(args, format);
        vsnprintf(buffer, sizeof(buffer), format, args);
        va_end(args);

        // 1. Android Logcat output
        __android_log_print(ANDROID_LOG_INFO, tag, "%s", buffer);

        // 2. Persistent file log strictly in Android default Download/ninmods/ directory
        static std::mutex s_mutex;
        std::lock_guard<std::mutex> lock(s_mutex);

        auto now = std::chrono::system_clock::now();
        auto in_time_t = std::chrono::system_clock::to_time_t(now);
        auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()) % 1000;

        char timeStr[64];
        struct tm tm_buf;
        localtime_r(&in_time_t, &tm_buf);
        snprintf(timeStr, sizeof(timeStr), "%04d-%02d-%02d %02d:%02d:%02d.%03d",
                 tm_buf.tm_year + 1900, tm_buf.tm_mon + 1, tm_buf.tm_mday,
                 tm_buf.tm_hour, tm_buf.tm_min, tm_buf.tm_sec, (int)ms.count());

        // Automatically ensure the dedicated ninmods folder exists inside the official Download directory
        mkdir("/storage/emulated/0/Download", 0777);
        mkdir("/storage/emulated/0/Download/ninmods", 0777);

        const char* logPath = "/storage/emulated/0/Download/ninmods/ninmods.log";
        FILE* f = fopen(logPath, "a");
        if (f) {
            fprintf(f, "[%s] [%s] %s\n", timeStr, tag, buffer);
            fflush(f);
            fclose(f);
            chmod(logPath, 0666);
        }
    }
};

#define NIN_LOGI(tag, fmt, ...) NinLogger::Log(tag, fmt, ##__VA_ARGS__)
#define NIN_LOGD(tag, fmt, ...) NinLogger::Log(tag, "[DEBUG] " fmt, ##__VA_ARGS__)
#define NIN_LOGE(tag, fmt, ...) NinLogger::Log(tag, "[ERROR] " fmt, ##__VA_ARGS__)
