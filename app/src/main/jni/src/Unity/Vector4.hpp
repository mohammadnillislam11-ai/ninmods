#pragma once

struct Vector4 {
    union {
        struct {
            float X;
            float Y;
            float Z;
            float W;
        };
        float data[4];
    };

    Vector4() : X(0.0f), Y(0.0f), Z(0.0f), W(0.0f) {}
    Vector4(float x, float y, float z, float w) : X(x), Y(y), Z(z), W(w) {}

    static Vector4 Zero() {
        return Vector4(0.0f, 0.0f, 0.0f, 0.0f);
    }
};
