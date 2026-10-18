#ifndef UNITY_MATRIX4X4_H
#define UNITY_MATRIX4X4_H

#include "Vector3.hpp"
#include <cmath>

struct Matrix4x4 {
    float m[16];

    inline bool WorldToScreen(const Vector3& pos, float screenWidth, float screenHeight, Vector3& out) const {
        float v9 = (pos.x * m[0]) + (pos.y * m[4]) + (pos.z * m[8]) + m[12];
        float v10 = (pos.x * m[1]) + (pos.y * m[5]) + (pos.z * m[9]) + m[13];
        float v12 = (pos.x * m[3]) + (pos.y * m[7]) + (pos.z * m[11]) + m[15];
        if (v12 < 0.001f) return false;
        float halfW = screenWidth * 0.5f;
        float halfH = screenHeight * 0.5f;
        out.x = halfW + (halfW * v9) / v12;
        out.y = halfH - (halfH * v10) / v12;
        out.z = v12;
        return true;
    }

    // Projects any 3D coordinate (including behind camera) for 360° radar tracking
    inline bool Project360(const Vector3& pos, float screenWidth, float screenHeight, Vector3& out) const {
        float v9 = (pos.x * m[0]) + (pos.y * m[4]) + (pos.z * m[8]) + m[12];
        float v10 = (pos.x * m[1]) + (pos.y * m[5]) + (pos.z * m[9]) + m[13];
        float v12 = (pos.x * m[3]) + (pos.y * m[7]) + (pos.z * m[11]) + m[15];
        float halfW = screenWidth * 0.5f;
        float halfH = screenHeight * 0.5f;
        float absZ = std::abs(v12);
        if (absZ < 0.0001f) absZ = 0.0001f;

        if (v12 < 0.0f) {
            // Invert vector so it points towards rear boundary of the screen
            out.x = halfW - (halfW * v9) / absZ;
            out.y = halfH + (halfH * v10) / absZ;
        } else {
            out.x = halfW + (halfW * v9) / absZ;
            out.y = halfH - (halfH * v10) / absZ;
        }
        out.z = v12;
        return true;
    }
};

#endif // UNITY_MATRIX4X4_H
