#ifndef VECTOR3_MATH_H
#define VECTOR3_MATH_H

#include "Vector3Decl.h"

inline Vector3 Vector3::zero() { return Vector3(0, 0, 0); }
inline Vector3 Vector3::One() { return Vector3(1, 1, 1); }
inline Vector3 Vector3::Right() { return Vector3(1, 0, 0); }
inline Vector3 Vector3::Left() { return Vector3(-1, 0, 0); }
inline Vector3 Vector3::Up() { return Vector3(0, 1, 0); }
inline Vector3 Vector3::Down() { return Vector3(0, -1, 0); }
inline Vector3 Vector3::Forward() { return Vector3(0, 0, 1); }
inline Vector3 Vector3::Backward() { return Vector3(0, 0, -1); }

inline float Vector3::Dot(Vector3 lhs, Vector3 rhs) {
    return lhs.x * rhs.x + lhs.y * rhs.y + lhs.z * rhs.z;
}

inline Vector3 Vector3::Cross(Vector3 lhs, Vector3 rhs) {
    return Vector3(
        lhs.y * rhs.z - lhs.z * rhs.y,
        lhs.z * rhs.x - lhs.x * rhs.z,
        lhs.x * rhs.y - lhs.y * rhs.x
    );
}

inline float Vector3::Magnitude(Vector3 vector) {
    return sqrtf(vector.x * vector.x + vector.y * vector.y + vector.z * vector.z);
}

inline float Vector3::Sqrmagnitude(Vector3 vector) {
    return vector.x * vector.x + vector.y * vector.y + vector.z * vector.z;
}

inline float Vector3::Distance(Vector3 a, Vector3 b) {
    Vector3 diff = a - b;
    return sqrtf(diff.x * diff.x + diff.y * diff.y + diff.z * diff.z);
}

inline float Vector3::Distance2(Vector3 a, Vector3 b) {
    Vector3 diff = a - b;
    return diff.x * diff.x + diff.y * diff.y + diff.z * diff.z;
}

inline Vector3 Vector3::Normalize(Vector3 value) {
    float mag = Magnitude(value);
    if (mag > 1e-5f) return value / mag;
    return zero();
}

inline Vector3 Vector3::Normalized(Vector3 value) {
    return Normalize(value);
}

inline float Vector3::magnitude() const { return Magnitude(*this); }
inline float Vector3::sqrMagnitude() const { return Sqrmagnitude(*this); }
inline Vector3 Vector3::normalized() const { return Normalize(*this); }

inline float Vector3::Angle(Vector3 a, Vector3 b) {
    float magMul = Magnitude(a) * Magnitude(b);
    if (magMul < 1e-5f) return 0.0f;
    float v = Dot(a, b) / magMul;
    v = fmaxf(v, -1.0f);
    v = fminf(v, 1.0f);
    return acosf(v);
}

inline Vector3 Vector3::Lerp(Vector3 a, Vector3 b, float t) {
    if (t < 0.0f) t = 0.0f;
    if (t > 1.0f) t = 1.0f;
    return a + (b - a) * t;
}

inline Vector3 Vector3::LerpUnclamped(Vector3 a, Vector3 b, float t) {
    return a + (b - a) * t;
}

inline bool Vector3::operator==(const Vector3& other) const {
    return fabsf(x - other.x) < 1e-5f && fabsf(y - other.y) < 1e-5f && fabsf(z - other.z) < 1e-5f;
}
inline bool Vector3::operator!=(const Vector3& other) const { return !(*this == other); }
inline Vector3 Vector3::operator+(const Vector3& other) const { return Vector3(x + other.x, y + other.y, z + other.z); }
inline Vector3 Vector3::operator-(const Vector3& other) const { return Vector3(x - other.x, y - other.y, z - other.z); }
inline Vector3 Vector3::operator*(float d) const { return Vector3(x * d, y * d, z * d); }
inline Vector3 Vector3::operator/(float d) const { return Vector3(x / d, y / d, z / d); }
inline Vector3& Vector3::operator+=(const Vector3& other) { x += other.x; y += other.y; z += other.z; return *this; }
inline Vector3& Vector3::operator-=(const Vector3& other) { x -= other.x; y -= other.y; z -= other.z; return *this; }
inline Vector3& Vector3::operator*=(float d) { x *= d; y *= d; z *= d; return *this; }
inline Vector3& Vector3::operator/=(float d) { x /= d; y /= d; z /= d; return *this; }

#endif // VECTOR3_MATH_H
