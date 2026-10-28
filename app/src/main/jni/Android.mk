LOCAL_PATH := $(call my-dir)

# --- Module: ninmods ---
include $(CLEAR_VARS)

LOCAL_MODULE    := ninmods

LOCAL_SRC_FILES := \
    src/Client.cpp \
    src/Engine/NinliteEngine.cpp \
    src/Bridge/SecurityVaultJNI.cpp \
    src/Menu/Menu.cpp \
    src/Menu/Jni.cpp \
    src/Menu/Preferences.cpp \
    src/Menu/Setup.cpp \
    src/imgui/imgui.cpp \
    src/imgui/imgui_draw.cpp \
    src/imgui/imgui_tables.cpp \
    src/imgui/imgui_widgets.cpp \
    src/imgui/imgui_impl_opengl3.cpp \
    src/imgui/imgui_impl_android.cpp \
    src/UI/NativeOverlayController.cpp \
    src/Icon/TextureLoader.cpp \
    src/Detection/GameDetection.cpp \
    src/ninlite_daemon/Namegun.cpp

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/src \
    $(LOCAL_PATH)/src/Detection \
    $(LOCAL_PATH)/src/Engine \
    $(LOCAL_PATH)/src/Bridge \
    $(LOCAL_PATH)/src/imgui \
    $(LOCAL_PATH)/src/Includes \
    $(LOCAL_PATH)/src/Includes/glm \
    $(LOCAL_PATH)/src/Menu \
    $(LOCAL_PATH)/src/Unity \
    $(LOCAL_PATH)/src/UI \
    $(LOCAL_PATH)/src/UI/Popups \
    $(LOCAL_PATH)/src/Icon \
    $(LOCAL_PATH)/src/ninlite_daemon


LOCAL_CPPFLAGS  := -fvisibility=hidden -DNDEBUG -std=c++17 -fexceptions -frtti
LOCAL_CFLAGS    := -Wno-error=format-security -fpermissive -fvisibility=hidden -fexceptions -frtti -DNDEBUG
LOCAL_LDFLAGS   := -Wl,--exclude-libs,ALL -s -latomic
LOCAL_LDLIBS    := -llog -landroid -lGLESv3 -lEGL -lz

include $(BUILD_SHARED_LIBRARY)

# --- Module: ninlite_daemon (Pure Root Daemon Engine (root daemon architecture)) ---
include $(CLEAR_VARS)
LOCAL_MODULE    := ninlite_daemon
LOCAL_SRC_FILES := src/ninlite_daemon/ninlite_daemon.cpp src/ninlite_daemon/Namegun.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)/src $(LOCAL_PATH)/src/Includes $(LOCAL_PATH)/src/Unity $(LOCAL_PATH)/src/ninlite_daemon
LOCAL_CPPFLAGS  := -fvisibility=hidden -DNDEBUG -std=c++17 -fexceptions -frtti
LOCAL_CFLAGS    := -Wno-error=format-security -fpermissive -fvisibility=hidden -fexceptions -frtti -DNDEBUG
LOCAL_LDFLAGS   := -Wl,--exclude-libs,ALL -s
LOCAL_LDLIBS    := -llog -lz
include $(BUILD_EXECUTABLE)

