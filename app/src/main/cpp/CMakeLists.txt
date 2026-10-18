cmake_minimum_required(VERSION 3.22.1)
project("ninmods_mem")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_definitions(-DkNO_KEYSTONE)

find_library(log-lib log)
find_library(android-lib android)

# 1. Main Shared Library (Loaded by Kotlin Main App via JNI)
add_library(ninmods_mem SHARED
    MemoryNative.cpp
    DriverClient.cpp
    UnityTransform.cpp
    EntityScanner.cpp
    ScannerBridge.cpp
    NativeCombat.cpp
    daemon/DaemonMem.cpp
    daemon/SocketClient.cpp
)

target_include_directories(ninmods_mem PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/daemon
    ${CMAKE_CURRENT_SOURCE_DIR}/Unity
)

target_link_libraries(ninmods_mem
    ${log-lib}
    ${android-lib}
)

# 2. Standalone Master Root Daemon Executable
add_executable(nin_daemon
    daemon/DaemonMain.cpp
    daemon/SocketServer.cpp
    daemon/DaemonMem.cpp
    DriverClient.cpp
    EntityScanner.cpp
    UnityTransform.cpp
    NativeCombat.cpp
)

target_include_directories(nin_daemon PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/daemon
    ${CMAKE_CURRENT_SOURCE_DIR}/Unity
)

target_link_libraries(nin_daemon
    ${log-lib}
)

add_custom_command(TARGET nin_daemon POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory "${CMAKE_CURRENT_SOURCE_DIR}/../assets"
    COMMAND ${CMAKE_COMMAND} -E copy "$<TARGET_FILE:nin_daemon>" "${CMAKE_CURRENT_SOURCE_DIR}/../assets/nin_daemon_${ANDROID_ABI}"
)
