#ifndef VECTOR2_DECL_H
#define VECTOR2_DECL_H

#include <string>
#define _USE_MATH_DEFINES
#include <math.h>

struct Vector2 {
    union {
        struct {
            float x;
            float y;
        };
        float data[2];
    };

    inline Vector2();
    inline Vector2(float data[]);
    inline Vector2(float value);
    inline Vector2(float x, float y);

    static inline Vector2 zero();
    static inline Vector2 one();
    static inline Vector2 right();
    static inline Vector2 left();
    static inline Vector2 up();
    static inline Vector2 down();

    static inline float Angle(Vector2 a, Vector2 b);
    static inline Vector2 ClampMagnitude(Vector2 vector, float maxLength);
    static inline float Distance(Vector2 a, Vector2 b);
    static inline float Dot(Vector2 lhs, Vector2 rhs);
    static inline Vector2 Lerp(Vector2 a, Vector2 b, float t);
    static inline Vector2 LerpUnclamped(Vector2 a, Vector2 b, float t);
    static inline Vector2 Max(Vector2 lhs, Vector2 rhs);
    static inline Vector2 Min(Vector2 lhs, Vector2 rhs);
    static inline Vector2 MoveTowards(Vector2 current, Vector2 target, float maxDistanceDelta);
    static inline Vector2 Normalize(Vector2 value);
    static inline Vector2 Scale(Vector2 a, Vector2 b);
    static inline float Magnitude(Vector2 vector);
    static inline float Sqrmagnitude(Vector2 vector);

    inline float magnitude() const;
    inline Vector2 normalized() const;
    inline float sqrMagnitude() const;
    inline void Set(float newX, float newY);
    inline std::string ToString();

    inline bool operator==(const Vector2& other) const;
    inline bool operator!=(const Vector2& other) const;
    inline Vector2 operator+(const Vector2& other) const;
    inline Vector2 operator-(const Vector2& other) const;
    inline Vector2 operator*(float d) const;
    inline Vector2 operator/(float d) const;
    inline Vector2& operator+=(const Vector2& other);
    inline Vector2& operator-=(const Vector2& other);
    inline Vector2& operator*=(float d);
    inline Vector2& operator/=(float d);
};

inline Vector2::Vector2() : x(0), y(0) {}
inline Vector2::Vector2(float data[]) : x(data[0]), y(data[1]) {}
inline Vector2::Vector2(float value) : x(value), y(value) {}
inline Vector2::Vector2(float x, float y) : x(x), y(y) {}

#endif // VECTOR2_DECL_H
