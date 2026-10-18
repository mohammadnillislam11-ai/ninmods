#ifndef UNITY_TRANSFORM_H
#define UNITY_TRANSFORM_H

#include <cstdint>
#include "Unity/Vector3.hpp"
#include "Unity/Quaternion.h"

struct TransformMatrixData {
    float position[4];
    Quaternion rotation;
    float scale[4];
};

Vector3 GetTransformPosition(uint64_t transform, uint64_t handle, int driverFd);
Vector3 GetNodePosition(uint64_t nodeTransform, uint64_t handle, int driverFd);

#endif // UNITY_TRANSFORM_H
