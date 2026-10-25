#if __has_include(<GLES2/gl2.h>)
#include <GLES2/gl2.h>
#else
#include "../Includes/android_ide_stubs/GLES2/gl2.h"
#endif

extern GLuint g_NinLogoTexture;
extern int g_NinLogoWidth;
extern int g_NinLogoHeight;

extern GLuint g_EagleLogoTexture;
extern int g_EagleLogoWidth;
extern int g_EagleLogoHeight;

bool LoadNinLogoTexture();
bool LoadEagleLogoTexture();
