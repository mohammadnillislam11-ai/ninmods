#ifndef QUATERNION_MATH_H
#define QUATERNION_MATH_H

#include "QuaternionDecl.h"

inline Quaternion Quaternion::identity() { return Quaternion(0, 0, 0, 1); }

inline float Quaternion::Dot(Quaternion a, Quaternion b) {
    return a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
}

inline Quaternion Quaternion::Normalize(Quaternion q) {
    float mag = sqrtf(Dot(q, q));
    if (mag < 1e-5f) return identity();
    return Quaternion(q.x / mag, q.y / mag, q.z / mag, q.w / mag);
}

inline Quaternion Quaternion::Inverse(Quaternion rotation) {
    float norm = Dot(rotation, rotation);
    if (norm > 0.0f) {
        float invNorm = 1.0f / norm;
        return Quaternion(-rotation.x * invNorm, -rotation.y * invNorm, -rotation.z * invNorm, rotation.w * invNorm);
    }
    return identity();
}

inline Quaternion Quaternion::Euler(float x, float y, float z) {
    float radX = x * 0.5f * (3.14159265358979323846f / 180.0f);
    float radY = y * 0.5f * (3.14159265358979323846f / 180.0f);
    float radZ = z * 0.5f * (3.14159265358979323846f / 180.0f);

    float c1 = cosf(radY), s1 = sinf(radY);
    float c2 = cosf(radZ), s2 = sinf(radZ);
    float c3 = cosf(radX), s3 = sinf(radX);

    return Quaternion(
        s1 * s2 * c3 + c1 * c2 * s3,
        s1 * c2 * c3 + c1 * s2 * s3,
        c1 * s2 * c3 - s1 * c2 * s3,
        c1 * c2 * c3 - s1 * s2 * s3
    );
}

inline Quaternion Quaternion::Euler(Vector3 euler) { return Euler(euler.x, euler.y, euler.z); }

inline Quaternion& Quaternion::operator+=(const Quaternion rhs) {
    x += rhs.x; y += rhs.y; z += rhs.z; w += rhs.w;
    return *this;
}

inline Quaternion& Quaternion::operator-=(const Quaternion rhs) {
    x -= rhs.x; y -= rhs.y; z -= rhs.z; w -= rhs.w;
    return *this;
}

inline Quaternion& Quaternion::operator*=(const float rhs) {
    x *= rhs; y *= rhs; z *= rhs; w *= rhs;
    return *this;
}

inline Quaternion& Quaternion::operator*=(const Quaternion rhs) {
    float qx = w * rhs.x + x * rhs.w + y * rhs.z - z * rhs.y;
    float qy = w * rhs.y + y * rhs.w + z * rhs.x - x * rhs.z;
    float qz = w * rhs.z + z * rhs.w + x * rhs.y - y * rhs.x;
    float qw = w * rhs.w - x * rhs.x - y * rhs.y - z * rhs.z;
    x = qx; y = qy; z = qz; w = qw;
    return *this;
}

inline Vector3 operator*(Quaternion lhs, const Vector3 rhs) {
    Vector3 u = Vector3(lhs.x, lhs.y, lhs.z);
    float s = lhs.w;
    return u * (Vector3::Dot(u, rhs) * 2.0f)
           + rhs * (s * s - Vector3::Dot(u, u))
           + Vector3::Cross(u, rhs) * (2.0f * s);
}

inline bool operator==(const Quaternion lhs, const Quaternion rhs) {
    return lhs.x == rhs.x && lhs.y == rhs.y && lhs.z == rhs.z && lhs.w == rhs.w;
}

inline bool operator!=(const Quaternion lhs, const Quaternion rhs) {
    return !(lhs == rhs);
}

inline Quaternion Quaternion::FromToRotation(Vector3 fromDirection, Vector3 toDirection) {
    Vector3 v0 = Vector3::Normalized(fromDirection);
    Vector3 v1 = Vector3::Normalized(toDirection);
    float d = Vector3::Dot(v0, v1);
    if (d >= 1.0f - 1e-5f) return identity();
    if (d <= -1.0f + 1e-5f) {
        Vector3 axis = Vector3::Cross(Vector3(1, 0, 0), v0);
        if (Vector3::Sqrmagnitude(axis) < 1e-5f)
            axis = Vector3::Cross(Vector3(0, 1, 0), v0);
        axis = Vector3::Normalized(axis);
        return Quaternion(axis.x, axis.y, axis.z, 0.0f);
    }
    Vector3 c = Vector3::Cross(v0, v1);
    float s = sqrtf((1.0f + d) * 2.0f);
    float invs = 1.0f / s;
    return Normalize(Quaternion(c.x * invs, c.y * invs, c.z * invs, s * 0.5f));
}

inline Quaternion Quaternion::LookRotation(Vector3 forward) {
    return LookRotation(forward, Vector3(0, 1, 0));
}

inline Quaternion Quaternion::LookRotation(Vector3 forward, Vector3 up) {
    forward = Vector3::Normalized(forward);
    up = Vector3::Normalized(up);
    if (Vector3::Sqrmagnitude(forward) < 1e-5f || Vector3::Sqrmagnitude(up) < 1e-5f)
        return identity();
    if (1.0f - fabsf(Vector3::Dot(forward, up)) < 1e-5f)
        return FromToRotation(Vector3::Forward(), forward);

    Vector3 right = Vector3::Normalized(Vector3::Cross(up, forward));
    up = Vector3::Cross(forward, right);

    Quaternion quaternion;
    float radicand = right.x + up.y + forward.z;
    if (radicand > 0.0f) {
        quaternion.w = sqrtf(1.0f + radicand) * 0.5f;
        float recip = 1.0f / (4.0f * quaternion.w);
        quaternion.x = (up.z - forward.y) * recip;
        quaternion.y = (forward.x - right.z) * recip;
        quaternion.z = (right.y - up.x) * recip;
    } else if (right.x >= up.y && right.x >= forward.z) {
        quaternion.x = sqrtf(1.0f + right.x - up.y - forward.z) * 0.5f;
        float recip = 1.0f / (4.0f * quaternion.x);
        quaternion.w = (up.z - forward.y) * recip;
        quaternion.z = (forward.x + right.z) * recip;
        quaternion.y = (right.y + up.x) * recip;
    } else if (up.y > forward.z) {
        quaternion.y = sqrtf(1.0f - right.x + up.y - forward.z) * 0.5f;
        float recip = 1.0f / (4.0f * quaternion.y);
        quaternion.z = (up.z + forward.y) * recip;
        quaternion.w = (forward.x - right.z) * recip;
        quaternion.x = (right.y + up.x) * recip;
    } else {
        quaternion.z = sqrtf(1.0f - right.x - up.y + forward.z) * 0.5f;
        float recip = 1.0f / (4.0f * quaternion.z);
        quaternion.y = (up.z + forward.y) * recip;
        quaternion.x = (forward.x + right.z) * recip;
        quaternion.w = (right.y - up.x) * recip;
    }
    return quaternion;
}

inline std::string to_string(Quaternion a) {
    return std::to_string(a.x) + std::string(", ") + std::to_string(a.y) + std::string(", ") + std::to_string(a.z) + std::string(", ") + std::to_string(a.w);
}

#endif // QUATERNION_MATH_H
