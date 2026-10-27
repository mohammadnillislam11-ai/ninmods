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
 * Rule 15: every persistent log file lives inside the Android Download
 * directory, never in the root of the sdcard.
 *
 * RELIABILITY NOTE (previous implementation was a performance bug):
 * The old logger executed two mkdir() + one fopen() + one fflush() +
 * one fclose() + one chmod() for EVERY log line, all under a process wide
 * mutex. Because the engine logs from the aim thread (250 Hz) and from the
 * render thread, that turned every single log call into a multi millisecond
 * blocking filesystem operation and serialised the whole engine behind one
 * mutex. The log file is now opened ONCE and kept open; the stream is flushed
 * in batches so the frame loop never blocks on the storage device.
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
        static unsigned int s_pending = 0;

        if (s_file == nullptr) {
            mkdir("/storage/emulated/0/Download", 0777);
            mkdir("/storage/emulated/0/Download/ninmods", 0777);
            s_file = fopen("/storage/emulated/0/Download/ninmods/ninmods.log", "a");
            if (s_file != nullptr) {
                chmod("/storage/emulated/0/Download/ninmods/ninmods.log", 0666);
            }
        }
        if (s_file == nullptr) {
            return;   /* storage not available - logcat only */
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

        /* Batch the flushes - a disk sync per line is what killed the frame
         * budget before. 32 lines per flush keeps the file readable in real
         * time while removing ~97% of the IOPS. */
        if (++s_pending >= 32) {
            fflush(s_file);
            s_pending = 0;
        }

        if (ferror(s_file)) {
            /* The file was removed or the storage died - reopen once. */
            fclose(s_file);
            s_file = nullptr;
            s_pending = 0;
        }
    }
};

#define NIN_LOGI(tag, fmt, ...) NinLogger::Log(tag, fmt, ##__VA_ARGS__)
#define NIN_LOGD(tag, fmt, ...) NinLogger::LogToLogcat(tag, "[DEBUG] " fmt, ##__VA_ARGS__)
#define NIN_LOGE(tag, fmt, ...) NinLogger::Log(tag, "[ERROR] " fmt, ##__VA_ARGS__)
