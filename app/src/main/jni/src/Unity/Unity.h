#pragma once

#include <android/log.h>
#include <cmath>
#include <cstdint>
#include "Vector3.hpp"
#include "Vector2.hpp"
#include "Quaternion.hpp"

#ifndef LOG_TAG
#define LOG_TAG "DAEMON"
#endif

#ifndef LOGE
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#endif

/**
 * Normalizes an angle into the [0, 360] range.
 *
 * @param angle Raw angle in degrees.
 * @return Normalized angle.
 */
inline float NormalizeAngle(float angle) {
    while (angle > 360.0f) angle -= 360.0f;
    while (angle < 0.0f) angle += 360.0f;
    return angle;
}

/**
 * Normalizes a 3D Euler angle vector into standard degrees.
 *
 * @param angles Vector of Euler angles.
 * @return Normalized Euler vector.
 */
inline Vector3 NormalizeAngles(Vector3 angles) {
    angles.X = NormalizeAngle(angles.X);
    angles.Y = NormalizeAngle(angles.Y);
    angles.Z = NormalizeAngle(angles.Z);
    return angles;
}

/**
 * Converts a rotation Quaternion to Euler angles in degrees.
 *
 * @param q1 Input rotation quaternion.
 * @return Euler angles representation.
 */
inline Vector3 ToEulerRad(Quaternion q1) {
    const float Rad2Deg = 360.0f / (M_PI * 2.0f);
    float sqw = q1.W * q1.W;
    float sqx = q1.X * q1.X;
    float sqy = q1.Y * q1.Y;
    float sqz = q1.Z * q1.Z;
    float unit = sqx + sqy + sqz + sqw;
    float test = q1.X * q1.W - q1.Y * q1.Z;
    Vector3 v;

    if (test > 0.4995f * unit) {
        v.Y = 2.0f * atan2f(q1.Y, q1.X);
        v.X = M_PI / 2.0f;
        v.Z = 0.0f;
        return NormalizeAngles(v * Rad2Deg);
    }
    if (test < -0.4995f * unit) {
        v.Y = -2.0f * atan2f(q1.Y, q1.X);
        v.X = -M_PI / 2.0f;
        v.Z = 0.0f;
        return NormalizeAngles(v * Rad2Deg);
    }
    Quaternion q(q1.W, q1.Z, q1.X, q1.Y);
    v.Y = atan2f(2.0f * q.X * q.W + 2.0f * q.Y * q.Z, 1.0f - 2.0f * (q.Z * q.Z + q.W * q.W));
    v.X = asinf(2.0f * (q.X * q.Z - q.W * q.Y));
    v.Z = atan2f(2.0f * q.X * q.Y + 2.0f * q.Z * q.W, 1.0f - 2.0f * (q.Y * q.Y + q.Z * q.Z));
    return NormalizeAngles(v * Rad2Deg);
}

/**
 * Computes LookRotation quaternion toward a target with vertical bias.
 *
 * @param targetLocation World position of the target.
 * @param y_bias Vertical offset bias.
 * @param myLoc World position of source camera / player.
 * @return Target rotation quaternion.
 */
inline Quaternion GetRotationToLocation(Vector3 targetLocation, float y_bias, Vector3 myLoc) {
    return Quaternion::LookRotation((targetLocation + Vector3(0.0f, y_bias, 0.0f)) - myLoc, Vector3(0.0f, 1.0f, 0.0f));
}

/**
 * Standard IL2CPP array memory layout template.
 */
template <typename T>
struct monoArray {
    void* klass;
    void* monitor;
    void* bounds;
    int   max_length;
    T vector[1];

    int getLength() {
        return (this != nullptr) ? max_length : 0;
    }
    T* getPointer() {
        return vector;
    }
};

/**
 * Standard IL2CPP list memory layout template.
 */
template <typename T>
struct monoList {
    void *unk0;
    void *unk1;
    monoArray<T> *items;
    int size;
    int version;

    T* getItems() {
        return (items != nullptr) ? items->getPointer() : nullptr;
    }
    int getSize() {
        return size;
    }
};

/**
 * Standard IL2CPP dictionary memory layout template.
 */
template <typename K, typename V>
struct monoDictionary {
    void *unk0;
    void *unk1;
    void *table;
    void *linkSlots;
    monoArray<K> *keys;
    monoArray<V> *values;
    int touchedSlots;
    int emptySlot;
    int size;

    K* getKeys() {
        return (keys != nullptr) ? keys->getPointer() : nullptr;
    }
    V* getValues() {
        return (values != nullptr) ? values->getPointer() : nullptr;
    }
    int getNumKeys() {
        return (keys != nullptr) ? keys->getLength() : 0;
    }
    int getNumValues() {
        return (values != nullptr) ? values->getLength() : 0;
    }
};

/**
 * Standard IL2CPP string memory layout structure.
 */
typedef struct _monoString {
    void* klass;
    void* monitor;
    int length;    
    char16_t chars[1];
    
    int getLength() { return length; }
    char16_t* getChars() { return chars; }
} monoString;
