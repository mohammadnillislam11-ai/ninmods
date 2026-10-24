#pragma once

#define STB_IMAGE_STATIC
#define STB_IMAGE_IMPLEMENTATION
#include "../../Includes/stb_image.h"

#include <GLES3/gl3.h>
#include <cstdint>

/**
 * OpenGL ES3 Texture Helper using stb_image.h.
 * Loads PNG/JPEG memory buffers into OpenGL ES3 textures for ImGui rendering.
 */
class TextureHelper {
public:
    static bool LoadTextureFromMemory(const unsigned char* buffer, size_t len, GLuint* out_texture, int* out_width, int* out_height) {
        if (!buffer || len == 0 || !out_texture) return false;

        int image_width = 0;
        int image_height = 0;
        int channels = 0;
        unsigned char* image_data = stbi_load_from_memory(buffer, (int)len, &image_width, &image_height, &channels, 4);
        if (!image_data) return false;

        GLuint image_texture = 0;
        glGenTextures(1, &image_texture);
        glBindTexture(GL_TEXTURE_2D, image_texture);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, image_width, image_height, 0, GL_RGBA, GL_UNSIGNED_BYTE, image_data);
        stbi_image_free(image_data);

        *out_texture = image_texture;
        if (out_width) *out_width = image_width;
        if (out_height) *out_height = image_height;

        return true;
    }
};
