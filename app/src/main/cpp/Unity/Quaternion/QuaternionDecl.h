#ifndef QUATERNION_DECL_H
#define QUATERNION_DECL_H

#include <string>
#include <cmath>
#include "../Vector3.hpp"

struct Quaternion {
    float x;
    float y;
    float z;
    float w;

    inline Quaternion();
    inline Quaternion(float x, float y, float z, float w);

    static inline Quaternion identity();
    static inline float Angle(Quaternion a, Quaternion b);
    static inline Quaternion AngleAxis(float degress, Vector3 axis);
    static inline float Dot(Quaternion a, Quaternion b);
    static inline Quaternion Euler(float x, float y, float z);
    static inline Quaternion Euler(Vector3 euler);
    static inline Quaternion FromToRotation(Vector3 fromDirection, Vector3 toDirection);
    static inline Quaternion Inverse(Quaternion rotation);
    static inline Quaternion Lerp(Quaternion a, Quaternion b, float t);
    static inline Quaternion LerpUnclamped(Quaternion a, Quaternion b, float t);
    static inline Quaternion LookRotation(Vector3 forward);
    static inline Quaternion LookRotation(Vector3 forward, Vector3 up);
    static inline Quaternion Normalize(Quaternion q);
    static inline Quaternion RotateTowards(Quaternion from, Quaternion to, float maxDegreesDelta);
    static inline Quaternion Slerp(Quaternion a, Quaternion b, float t);
    static inline Quaternion SlerpUnclamped(Quaternion a, Quaternion b, float t);

    inline Quaternion eulerAngles() const;
    inline Quaternion normalized() const;

    inline void Set(float newX, float newY, float newZ, float newW);
    inline void SetFromToRotation(Vector3 fromDirection, Vector3 toDirection);
    inline void SetLookRotation(Vector3 view);
    inline void SetLookRotation(Vector3 view, Vector3 up);
    inline void ToAngleAxis(float &angle, Vector3 &axis);
    inline std::string ToString();

    inline Quaternion& operator+=(const Quaternion rhs);
    inline Quaternion& operator-=(const Quaternion rhs);
    inline Quaternion& operator*=(const float rhs);
    inline Quaternion& operator*=(const Quaternion rhs);
};

inline Quaternion::Quaternion() : x(0), y(0), z(0), w(1) {}
inline Quaternion::Quaternion(float x, float y, float z, float w) : x(x), y(y), z(z), w(w) {}

#endif // QUATERNION_DECL_H
