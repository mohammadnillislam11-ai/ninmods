#ifndef VINT3_MATH_H
#define VINT3_MATH_H

#include "VInt3Decl.h"

inline VInt3 VInt3::Zero() { return VInt3(0, 0, 0); }
inline VInt3 VInt3::One() { return VInt3(1, 1, 1); }
inline VInt3 VInt3::Right() { return VInt3(1, 0, 0); }
inline VInt3 VInt3::Left() { return VInt3(-1, 0, 0); }
inline VInt3 VInt3::Up() { return VInt3(0, 1, 0); }
inline VInt3 VInt3::Down() { return VInt3(0, -1, 0); }
inline VInt3 VInt3::Forward() { return VInt3(0, 0, 1); }
inline VInt3 VInt3::Backward() { return VInt3(0, 0, -1); }

inline int VInt3::Dot(VInt3 lhs, VInt3 rhs) {
    return lhs.X * rhs.X + lhs.Y * rhs.Y + lhs.Z * rhs.Z;
}

inline VInt3 VInt3::Cross(VInt3 lhs, VInt3 rhs) {
    return VInt3(
        lhs.Y * rhs.Z - lhs.Z * rhs.Y,
        lhs.Z * rhs.X - lhs.X * rhs.Z,
        lhs.X * rhs.Y - lhs.Y * rhs.X
    );
}

inline int VInt3::Magnitude(VInt3 vector) {
    return (int)sqrtf((float)(vector.X * vector.X + vector.Y * vector.Y + vector.Z * vector.Z));
}

inline int VInt3::Sqrmagnitude(VInt3 vector) {
    return vector.X * vector.X + vector.Y * vector.Y + vector.Z * vector.Z;
}

inline int VInt3::Distance(VInt3 a, VInt3 b) {
    VInt3 diff = a - b;
    return (int)sqrtf((float)(diff.X * diff.X + diff.Y * diff.Y + diff.Z * diff.Z));
}

inline int VInt3::magnitude() const { return Magnitude(*this); }
inline int VInt3::sqrMagnitude() const { return Sqrmagnitude(*this); }
inline void VInt3::Set(int newX, int newY, int newZ) { X = newX; Y = newY; Z = newZ; }

inline bool VInt3::operator==(const VInt3& other) const { return X == other.X && Y == other.Y && Z == other.Z; }
inline bool VInt3::operator!=(const VInt3& other) const { return !(*this == other); }
inline VInt3 VInt3::operator+(const VInt3& other) const { return VInt3(X + other.X, Y + other.Y, Z + other.Z); }
inline VInt3 VInt3::operator-(const VInt3& other) const { return VInt3(X - other.X, Y - other.Y, Z - other.Z); }
inline VInt3 VInt3::operator*(int d) const { return VInt3(X * d, Y * d, Z * d); }
inline VInt3 VInt3::operator/(int d) const { return VInt3(X / d, Y / d, Z / d); }
inline VInt3& VInt3::operator+=(const VInt3& other) { X += other.X; Y += other.Y; Z += other.Z; return *this; }
inline VInt3& VInt3::operator-=(const VInt3& other) { X -= other.X; Y -= other.Y; Z -= other.Z; return *this; }
inline VInt3& VInt3::operator*=(int d) { X *= d; Y *= d; Z *= d; return *this; }
inline VInt3& VInt3::operator/=(int d) { X /= d; Y /= d; Z /= d; return *this; }

#endif // VINT3_MATH_H
