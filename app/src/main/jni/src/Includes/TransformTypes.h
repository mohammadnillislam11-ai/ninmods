#pragma once

/**
 * SHARED MEMORY LAYOUT TYPES
 * --------------------------
 * The view matrix and the Unity TRS matrix are needed by BOTH the in-process
 * engine (GameEngine.h) and the root daemon (ninlite_daemon.cpp). They used
 * to be declared separately, which meant the daemon did not even compile
 * (unknown type name 'D3DMatrix' / 'TransformMatrix').
 *
 * This header is the single definition for both.
 */

#include "../Unity/Vector4.hpp"
#include "../Unity/Quaternion.hpp"

/** Row major 4x4 view matrix (as used by the Unity camera). */
struct D3DMatrix {
    float _11, _12, _13, _14;
    float _21, _22, _23, _24;
    float _31, _32, _33, _34;
    float _41, _42, _43, _44;
};

/** Unity Transform TRS matrix: position, rotation and scale. */
struct Matrix {
    Vector4 Position;
    Quaternion Rotation;
    Vector4 Scale;
};
