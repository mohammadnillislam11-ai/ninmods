package com.ninmods.menu.memory

/**
 * Game identification and runtime package constants.
 * ZERO offsets are defined in Kotlin.
 * ALL game memory offsets reside 100% exclusively in C++ (gameoffsets.h).
 */
object GameConfig {
    const val LIBIL2CPP = "libil2cpp.so"
    const val GAME_PACKAGE_FF = "com.dts.freefireth"
    const val GAME_PACKAGE_MAX = "com.dts.freefiremax"

    @Volatile
    var isMax: Boolean = false

    const val MAX_ENTITIES: Int = 64
}
