#ifndef VECTOR2_MATH_H
#define VECTOR2_MATH_H

#include "Vector2Decl.h"

inline Vector2 Vector2::zero() { return Vector2(0, 0); }
inline Vector2 Vector2::one() { return Vector2(1, 1); }
inline Vector2 Vector2::right() { return Vector2(1, 0); }
inline Vector2 Vector2::left() { return Vector2(-1, 0); }
inline Vector2 Vector2::up() { return Vector2(0, 1); }
inline Vector2 Vector2::down() { return Vector2(0, -1); }

inline float Vector2::Dot(Vector2 lhs, Vector2 rhs) {
    return lhs.x * rhs.x + lhs.y * rhs.y;
}

inline float Vector2::Magnitude(Vector2 vector) {
    return sqrtf(vector.x * vector.x + vector.y * vector.y);
}

inline float Vector2::Sqrmagnitude(Vector2 vector) {
    return vector.x * vector.x + vector.y * vector.y;
}

inline float Vector2::Distance(Vector2 a, Vector2 b) {
    Vector2 diff = a - b;
    return sqrtf(diff.x * diff.x + diff.y * diff.y);
}

inline Vector2 Vector2::Normalize(Vector2 value) {
    float mag = Magnitude(value);
    if (mag > 1e-5f) return value / mag;
    return zero();
}

inline float Vector2::magnitude() const { return Magnitude(*this); }
inline float Vector2::sqrMagnitude() const { return Sqrmagnitude(*this); }
inline Vector2 Vector2::normalized() const { return Normalize(*this); }
inline void Vector2::Set(float newX, float newY) { x = newX; y = newY; }

inline bool Vector2::operator==(const Vector2& other) const { return fabsf(x - other.x) < 1e-5f && fabsf(y - other.y) < 1e-5f; }
inline bool Vector2::operator!=(const Vector2& other) const { return !(*this == other); }
inline Vector2 Vector2::operator+(const Vector2& other) const { return Vector2(x + other.x, y + other.y); }
inline Vector2 Vector2::operator-(const Vector2& other) const { return Vector2(x - other.x, y - other.y); }
inline Vector2 Vector2::operator*(float d) const { return Vector2(x * d, y * d); }
inline Vector2 Vector2::operator/(float d) const { return Vector2(x / d, y / d); }
inline Vector2& Vector2::operator+=(const Vector2& other) { x += other.x; y += other.y; return *this; }
inline Vector2& Vector2::operator-=(const Vector2& other) { x -= other.x; y -= other.y; return *this; }
inline Vector2& Vector2::operator*=(float d) { x *= d; y *= d; return *this; }
inline Vector2& Vector2::operator/=(float d) { x /= d; y /= d; return *this; }

#endif // VECTOR2_MATH_H
