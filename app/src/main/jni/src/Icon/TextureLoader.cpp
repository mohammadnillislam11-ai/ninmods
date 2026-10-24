#include "TextureLoader.h"
#include <stdint.h>
#include <stdlib.h>
#include <GLES2/gl2.h>

#ifndef STB_IMAGE_STATIC
#define STB_IMAGE_STATIC
#endif
#define STB_IMAGE_IMPLEMENTATION
#define STBI_NO_STDIO
#include "stb_image.h"
#include "NinLogo.h"
#include "EagleLogo.h"

GLuint g_NinLogoTexture = 0;
int g_NinLogoWidth = 0;
int g_NinLogoHeight = 0;

GLuint g_EagleLogoTexture = 0;
int g_EagleLogoWidth = 0;
int g_EagleLogoHeight = 0;

bool LoadNinLogoTexture() {
    if (g_NinLogoTexture != 0) return true;

    int width = 0, height = 0, channels = 0;
    unsigned char* data = stbi_load_from_memory(nin_png_data, (int)nin_png_size, &width, &height, &channels, 4);
    if (!data) return false;

    g_NinLogoWidth = width;
    g_NinLogoHeight = height;

    glGenTextures(1, &g_NinLogoTexture);
    glBindTexture(GL_TEXTURE_2D, g_NinLogoTexture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
    stbi_image_free(data);
    return true;
}

bool LoadEagleLogoTexture() {
    if (g_EagleLogoTexture != 0) return true;

    int width = 0, height = 0, channels = 0;
    unsigned char* data = stbi_load_from_memory(eagle_png_data, (int)eagle_png_size, &width, &height, &channels, 4);
    if (!data) return false;

    g_EagleLogoWidth = width;
    g_EagleLogoHeight = height;

    glGenTextures(1, &g_EagleLogoTexture);
    glBindTexture(GL_TEXTURE_2D, g_EagleLogoTexture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
    stbi_image_free(data);
    return true;
}
