#include "UnityTransform.h"
#include "DriverClient.h"

Vector3 GetTransformPosition(uint64_t transform, uint64_t handle, int driverFd) {
    Vector3 pos(0.0f, 0.0f, 0.0f);
    if (transform == 0 || handle == 0) return pos;

    uint64_t transformObj = 0;
    if (!DriverRead(driverFd, handle, transform + 0x10, &transformObj, sizeof(transformObj)) || transformObj == 0) {
        return pos;
    }

    uint64_t objFields[2] = {0, 0}; // 0x38: matrixValue, 0x40: indexValue
    if (!DriverRead(driverFd, handle, transformObj + 0x38, objFields, sizeof(objFields))) {
        return pos;
    }
    const uint64_t matrixValue = objFields[0];
    const uint64_t indexValue = objFields[1];
    if (matrixValue == 0) return pos;

    uint64_t matrixLists[2] = {0, 0}; // 0x18: matrixListValue, 0x20: matrixIndicesValue
    if (!DriverRead(driverFd, handle, matrixValue + 0x18, matrixLists, sizeof(matrixLists))) {
        return pos;
    }
    const uint64_t matrixListValue = matrixLists[0];
    const uint64_t matrixIndicesValue = matrixLists[1];
    if (matrixListValue == 0 || matrixIndicesValue == 0) return pos;

    Vector3 resultValue(0.0f, 0.0f, 0.0f);
    DriverRead(driverFd, handle, matrixListValue + (indexValue * 0x30), &resultValue, sizeof(resultValue));

    int transformIndexValue = -1;
    DriverRead(driverFd, handle, matrixIndicesValue + (indexValue * 4), &transformIndexValue, sizeof(transformIndexValue));

    int tries = 0;
    const int maxTries = 25;
    while (transformIndexValue >= 0 && tries < maxTries) {
        tries++;
        TransformMatrixData tMatrix{};
        if (!DriverRead(driverFd, handle, matrixListValue + (transformIndexValue * 0x30), &tMatrix, sizeof(tMatrix))) {
            break;
        }

        Vector3 scaleVec(resultValue.x * tMatrix.scale[0],
                         resultValue.y * tMatrix.scale[1],
                         resultValue.z * tMatrix.scale[2]);

        Vector3 rotated = tMatrix.rotation * scaleVec;
        resultValue = Vector3(tMatrix.position[0], tMatrix.position[1], tMatrix.position[2]) + rotated;

        int nextIndex = -1;
        DriverRead(driverFd, handle, matrixIndicesValue + (transformIndexValue * 4), &nextIndex, sizeof(nextIndex));
        transformIndexValue = nextIndex;
    }

    if (tries < maxTries) {
        pos = resultValue;
    }
    return pos;
}

Vector3 GetNodePosition(uint64_t nodeTransform, uint64_t handle, int driverFd) {
    if (nodeTransform == 0 || handle == 0) return Vector3(0.0f, 0.0f, 0.0f);
    uint64_t transformValue = 0;
    if (!DriverRead(driverFd, handle, nodeTransform + 0x10, &transformValue, sizeof(transformValue)) || transformValue == 0) {
        return Vector3(0.0f, 0.0f, 0.0f);
    }
    return GetTransformPosition(transformValue, handle, driverFd);
}
