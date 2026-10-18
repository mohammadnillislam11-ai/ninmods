#ifndef VINT3_DECL_H
#define VINT3_DECL_H

#define _USE_MATH_DEFINES
#include <math.h>
#include <string.h>
#include <cmath>

struct VInt3 {
    union {
        struct {
            int X;
            int Y;
            int Z;
        };
        int data[3];
    };

    inline VInt3();
    inline VInt3(int data[]);
    inline VInt3(int value);
    inline VInt3(int x, int y);
    inline VInt3(int x, int y, int z);

    static inline VInt3 Zero();
    static inline VInt3 One();
    static inline VInt3 Right();
    static inline VInt3 Left();
    static inline VInt3 Up();
    static inline VInt3 Down();
    static inline VInt3 Forward();
    static inline VInt3 Backward();

    static inline int Angle(VInt3 a, VInt3 b);
    static inline VInt3 ClampMagnitude(VInt3 vector, int maxLength);
    static inline int Component(VInt3 a, VInt3 b);
    static inline VInt3 Cross(VInt3 lhs, VInt3 rhs);
    static inline int Distance(VInt3 a, VInt3 b);
    static inline int Dot(VInt3 lhs, VInt3 rhs);
    static inline VInt3 Lerp(VInt3 a, VInt3 b, float t);
    static inline VInt3 Max(VInt3 lhs, VInt3 rhs);
    static inline VInt3 Min(VInt3 lhs, VInt3 rhs);
    static inline VInt3 Scale(VInt3 a, VInt3 b);
    static inline int Magnitude(VInt3 vector);
    static inline int Sqrmagnitude(VInt3 vector);

    inline int magnitude() const;
    inline int sqrMagnitude() const;
    inline void Set(int newX, int newY, int newZ);

    inline bool operator==(const VInt3& other) const;
    inline bool operator!=(const VInt3& other) const;
    inline VInt3 operator+(const VInt3& other) const;
    inline VInt3 operator-(const VInt3& other) const;
    inline VInt3 operator*(int d) const;
    inline VInt3 operator/(int d) const;
    inline VInt3& operator+=(const VInt3& other);
    inline VInt3& operator-=(const VInt3& other);
    inline VInt3& operator*=(int d);
    inline VInt3& operator/=(int d);
};

inline VInt3::VInt3() : X(0), Y(0), Z(0) {}
inline VInt3::VInt3(int data[]) : X(data[0]), Y(data[1]), Z(data[2]) {}
inline VInt3::VInt3(int value) : X(value), Y(value), Z(value) {}
inline VInt3::VInt3(int x, int y) : X(x), Y(y), Z(0) {}
inline VInt3::VInt3(int x, int y, int z) : X(x), Y(y), Z(z) {}

#endif // VINT3_DECL_H
