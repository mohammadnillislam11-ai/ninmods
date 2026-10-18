#ifndef VECTOR3_DECL_H
#define VECTOR3_DECL_H

#include <string>
#define _USE_MATH_DEFINES
#include <math.h>

struct Vector3 {
	union {
		struct {
			float x;
			float y;
			float z;
		};
		float data[3];
	};
    inline Vector3();
    inline Vector3(float data[]);
    inline Vector3(float value);
    inline Vector3(float x, float y);
    inline Vector3(float x, float y, float z);
	
	static inline Vector3 zero();
    static inline Vector3 One();
    static inline Vector3 Right();
    static inline Vector3 Left();
    static inline Vector3 Up();
    static inline Vector3 Down();
    static inline Vector3 Forward();
    static inline Vector3 Backward();
    static inline float Angle(Vector3 a, Vector3 b);
    static inline Vector3 ClampMagnitude(Vector3 vector, float maxLength);
	static inline float Component(Vector3 a, Vector3 b);
    static inline Vector3 Cross(Vector3 lhs, Vector3 rhs);
    static inline float Distance(Vector3 a, Vector3 b);
    static inline float Distance2(Vector3 a, Vector3 b);
    static inline float Dot(Vector3 lhs, Vector3 rhs);
    static inline Vector3 FromSpherical(float rad, float theta, float phi);
    static inline Vector3 Lerp(Vector3 a, Vector3 b, float t);
    static inline Vector3 LerpUnclamped(Vector3 a, Vector3 b, float t);
    static inline Vector3 Max(Vector3 lhs, Vector3 rhs);
    static inline Vector3 Min(Vector3 lhs, Vector3 rhs);
    static inline Vector3 MoveTowards(Vector3 current, Vector3 target, float maxDistanceDelta);
    static inline Vector3 Normalize(Vector3 value);
    static inline Vector3 Normalized(Vector3 value);
    static inline Vector3 Orthogonal(Vector3 normal);
    static inline Vector3 Project(Vector3 vector, Vector3 onNormal);
    static inline Vector3 ProjectOnPlane(Vector3 vector, Vector3 planeNormal);
    static inline Vector3 Reflect(Vector3 inDirection, Vector3 inNormal);
    static inline Vector3 Scale(Vector3 a, Vector3 b);
    static inline Vector3 Slerp(Vector3 a, Vector3 b, float t);
    static inline Vector3 SlerpUnclamped(Vector3 a, Vector3 b, float t);
    static inline float Magnitude(Vector3 vector);
    static inline float Sqrmagnitude(Vector3 vector);
    static inline void ToSpherical(Vector3 vector, float &rad, float &theta, float &phi);
    inline float magnitude() const;
    inline Vector3 normalized() const;
    inline float sqrMagnitude() const;
    inline void Scale(Vector3 scale);
    inline void Set(float newX, float newY, float newZ);
    inline std::string ToString();
    inline bool operator==(const Vector3& other) const;
    inline bool operator!=(const Vector3& other) const;
    inline Vector3 operator+(const Vector3& other) const;
    inline Vector3 operator-(const Vector3& other) const;
    inline Vector3 operator*(float d) const;
    inline Vector3 operator/(float d) const;
    inline Vector3& operator+=(const Vector3& other);
    inline Vector3& operator-=(const Vector3& other);
    inline Vector3& operator*=(float d);
    inline Vector3& operator/=(float d);
};

inline Vector3::Vector3() : x(0), y(0), z(0) {}
inline Vector3::Vector3(float data[]) : x(data[0]), y(data[1]), z(data[2]) {}
inline Vector3::Vector3(float value) : x(value), y(value), z(value) {}
inline Vector3::Vector3(float x, float y) : x(x), y(y), z(0) {}
inline Vector3::Vector3(float x, float y, float z) : x(x), y(y), z(z) {}

#endif // VECTOR3_DECL_H
