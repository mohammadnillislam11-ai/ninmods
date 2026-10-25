LOCAL_PATH := $(call my-dir)

# --- Module: ninmods ---
include $(CLEAR_VARS)

LOCAL_MODULE    := ninmods

LOCAL_SRC_FILES := \
    src/Client.cpp \
    src/Socket/client.cpp \
    src/imgui/imgui.cpp \
    src/imgui/imgui_draw.cpp \
    src/imgui/imgui_tables.cpp \
    src/imgui/imgui_widgets.cpp \
    src/imgui/imgui_impl_opengl3.cpp \
    src/imgui/imgui_impl_android.cpp \
    src/UI/MenuRouter.cpp \
    src/UI/NativeOverlayController.cpp \
    src/UI/Brands/NinMenu.cpp \
    src/UI/Brands/BullMenu.cpp \
    src/UI/Brands/CrossMenu.cpp \
    src/UI/Brands/DeltaMenu.cpp \
    src/Icon/TextureLoader.cpp

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/src \
    $(LOCAL_PATH)/src/imgui \
    $(LOCAL_PATH)/src/Includes \
    $(LOCAL_PATH)/src/Includes/glm \
    $(LOCAL_PATH)/src/Socket \
    $(LOCAL_PATH)/src/Unity \
    $(LOCAL_PATH)/src/UI \
    $(LOCAL_PATH)/src/UI/Brands \
    $(LOCAL_PATH)/src/UI/Popups \
    $(LOCAL_PATH)/src/Icon

# Added -frtti to fix the dynamic_cast error
LOCAL_CPPFLAGS  := -fvisibility=hidden -DNDEBUG -std=c++17 -fexceptions -frtti
LOCAL_CFLAGS    := -Wno-error=format-security -fpermissive -fvisibility=hidden -fexceptions -frtti -DNDEBUG

# Moved -latomic here to fix the linker warning
LOCAL_LDFLAGS   := -Wl,--exclude-libs,ALL -s -latomic
LOCAL_LDLIBS    := -llog -landroid -lGLESv3 -lEGL -lz

LOCAL_ARM_MODE  := arm

include $(BUILD_SHARED_LIBRARY)
