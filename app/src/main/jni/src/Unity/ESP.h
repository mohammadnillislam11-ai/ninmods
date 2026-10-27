#ifndef ESP_H
#define ESP_H

#include <jni.h>
#include <algorithm>
#include "Color.hpp"
#include "Rect.hpp"

class ESP {
private:
    JNIEnv *_env;
    jobject _cvsView;
    jobject _cvs;

    // Cached JNI Method IDs to prevent runtime reflection stalls
    static inline jmethodID s_midGetWidth = nullptr;
    static inline jmethodID s_midGetHeight = nullptr;
    static inline jmethodID s_midDrawLine = nullptr;
    static inline jmethodID s_midDrawText = nullptr;
    static inline jmethodID s_midDrawName = nullptr;
    static inline jmethodID s_midDrawFilledCircle = nullptr;
    static inline jmethodID s_midDrawCircle = nullptr;
    static inline jmethodID s_midDrawFilledRect = nullptr;

    void ensureMethodIds() {
        if (!_env || !_cvsView || !_cvs) return;
        if (s_midDrawLine != nullptr) return;

        jclass cvsClass = _env->GetObjectClass(_cvs);
        if (cvsClass) {
            s_midGetWidth = _env->GetMethodID(cvsClass, "getWidth", "()I");
            s_midGetHeight = _env->GetMethodID(cvsClass, "getHeight", "()I");
            _env->DeleteLocalRef(cvsClass);
        }

        jclass viewClass = _env->GetObjectClass(_cvsView);
        if (viewClass) {
            s_midDrawLine = _env->GetMethodID(viewClass, "DrawLine",
                                              "(Landroid/graphics/Canvas;IIIIFFFFF)V");
            s_midDrawText = _env->GetMethodID(viewClass, "DrawText",
                                              "(Landroid/graphics/Canvas;IIIILjava/lang/String;FFF)V");
            s_midDrawName = _env->GetMethodID(viewClass, "DrawName",
                                              "(Landroid/graphics/Canvas;IIIILjava/lang/String;IFFF)V");
            s_midDrawFilledCircle = _env->GetMethodID(viewClass, "DrawFilledCircle",
                                                      "(Landroid/graphics/Canvas;IIIIFFF)V");
            s_midDrawCircle = _env->GetMethodID(viewClass, "DrawCircle",
                                                "(Landroid/graphics/Canvas;IIIIFFFF)V");
            s_midDrawFilledRect = _env->GetMethodID(viewClass, "DrawFilledRect",
                                                    "(Landroid/graphics/Canvas;IIIIFFFF)V");
            _env->DeleteLocalRef(viewClass);
        }
    }

public:
    ESP() : _env(nullptr), _cvsView(nullptr), _cvs(nullptr) {}

    ESP(JNIEnv *env, jobject cvsView, jobject cvs)
        : _env(env), _cvsView(cvsView), _cvs(cvs) {
        ensureMethodIds();
    }

    bool isValid() const {
        return (_env != nullptr && _cvsView != nullptr && _cvs != nullptr);
    }

    int getWidth() const {
        if (isValid() && s_midGetWidth) {
            return _env->CallIntMethod(_cvs, s_midGetWidth);
        }
        return 0;
    }

    int getHeight() const {
        if (isValid() && s_midGetHeight) {
            return _env->CallIntMethod(_cvs, s_midGetHeight);
        }
        return 0;
    }

    void DrawLine(Color color, float thickness, Vector3 start, Vector3 end) {
        if (isValid() && s_midDrawLine) {
            _env->CallVoidMethod(_cvsView, s_midDrawLine, _cvs,
                                 (jint)color.a, (jint)color.r, (jint)color.g, (jint)color.b,
                                 thickness, start.X, start.Y, end.X, end.Y);
        }
    }

    void DrawText(Color color, const char *txt, Vector2 pos, float size) {
        if (isValid() && s_midDrawText && txt != nullptr) {
            jstring s = _env->NewStringUTF(txt);
            if (s) {
                _env->CallVoidMethod(_cvsView, s_midDrawText, _cvs,
                                     (jint)color.a, (jint)color.r, (jint)color.g, (jint)color.b,
                                     s, pos.X, pos.Y, size);
                _env->DeleteLocalRef(s);
            }
        }
    }

    void DrawText2(Color color, float stroke, const char *txt, Vector2 pos, float size) {
        (void)stroke;
        DrawText(color, txt, pos, size);
    }

    void DrawName(Color color, const char *txt, Vector3 pos, float size) {
        if (isValid() && s_midDrawName && txt != nullptr) {
            jstring s = _env->NewStringUTF(txt);
            if (s) {
                _env->CallVoidMethod(_cvsView, s_midDrawName, _cvs,
                                     (jint)color.a, (jint)color.r, (jint)color.g, (jint)color.b,
                                     s, 0, pos.X, pos.Y, size);
                _env->DeleteLocalRef(s);
            }
        }
    }

    void DrawFilledCircle(Color color, Vector2 pos, float radius) {
        if (isValid() && s_midDrawFilledCircle) {
            _env->CallVoidMethod(_cvsView, s_midDrawFilledCircle, _cvs,
                                 (jint)color.a, (jint)color.r, (jint)color.g, (jint)color.b,
                                 pos.X, pos.Y, radius);
        }
    }

    void DrawCircle(Color color, float stroke, Vector2 pos, float radius) {
        if (isValid() && s_midDrawCircle) {
            _env->CallVoidMethod(_cvsView, s_midDrawCircle, _cvs,
                                 (jint)color.a, (jint)color.r, (jint)color.g, (jint)color.b,
                                 stroke, pos.X, pos.Y, radius);
        }
    }

    void DrawFilledRect(Color color, Vector3 pos, float width, float height) {
        if (isValid() && s_midDrawFilledRect) {
            _env->CallVoidMethod(_cvsView, s_midDrawFilledRect, _cvs,
                                 (jint)color.a, (jint)color.r, (jint)color.g, (jint)color.b,
                                 pos.X, pos.Y, width, height);
        }
    }

    void DrawFilledRect(Rect rect, Color color) {
        DrawFilledRect(color, Vector3(rect.x, rect.y, 0), rect.width, rect.height);
    }

    void DrawSkeleton(Color color, float thickness, float boxWidth, float boxHeight,
                      int screenWidth, int screenHeight, Vector3 headP, Vector3 NeckP,
                      Vector3 bodyP, Vector3 pernaEP, Vector3 pernaDP, Vector3 ombroD,
                      Vector3 ombroE, Vector3 DedoS, Vector3 maoDP) {
        (void)boxWidth;
        (void)boxHeight;
        auto DrawCircleAt = [&](Vector3 p, float r) {
            DrawCircle(color, thickness, Vector2(p.X, (float)screenHeight - p.Y), r);
        };
        auto DrawLineBetween = [&](Vector3 p1, Vector3 p2) {
            DrawLine(color, thickness,
                     Vector3((float)screenWidth - ((float)screenWidth - p1.X), (float)screenHeight - p1.Y, 0),
                     Vector3((float)screenWidth - ((float)screenWidth - p2.X), (float)screenHeight - p2.Y, 0));
        };

        DrawCircleAt(headP, 5.0f);
        DrawLineBetween(bodyP, NeckP);
        DrawLineBetween(ombroD, NeckP);
        DrawLineBetween(ombroE, NeckP);
        DrawLineBetween(pernaEP, bodyP);
        DrawLineBetween(pernaDP, bodyP);
        DrawLineBetween(DedoS, ombroE);
        DrawLineBetween(maoDP, ombroD);
    }

    void DrawBox(Color color, float stroke, Rect rect) {
        Vector3 v1 = Vector3(rect.x, rect.y, 0);
        Vector3 v2 = Vector3(rect.x + rect.width, rect.y, 0);
        Vector3 v3 = Vector3(rect.x + rect.width, rect.y + rect.height, 0);
        Vector3 v4 = Vector3(rect.x, rect.y + rect.height, 0);

        DrawLine(color, stroke, v1, v2);
        DrawLine(color, stroke, v2, v3);
        DrawLine(color, stroke, v3, v4);
        DrawLine(color, stroke, v4, v1);
    }

    void DrawBox4Line(int x, int y, int w, int h, Color color, float thickness) {
        float iw = (float)w / 4.0f;
        float ih = (float)h / 4.0f;
        float fx = (float)x;
        float fy = (float)y;
        float fw = (float)w;
        float fh = (float)h;

        DrawLine(color, thickness, Vector3(fx, fy, 0), Vector3(fx + iw, fy, 0));
        DrawLine(color, thickness, Vector3(fx + fw - iw, fy, 0), Vector3(fx + fw, fy, 0));
        DrawLine(color, thickness, Vector3(fx, fy, 0), Vector3(fx, fy + ih, 0));
        DrawLine(color, thickness, Vector3(fx + fw - 1, fy, 0), Vector3(fx + fw - 1, fy + ih, 0));

        DrawLine(color, thickness, Vector3(fx, fy + fh, 0), Vector3(fx + iw, fy + fh, 0));
        DrawLine(color, thickness, Vector3(fx + fw - iw, fy + fh, 0), Vector3(fx + fw, fy + fh, 0));
        DrawLine(color, thickness, Vector3(fx, fy + fh - ih, 0), Vector3(fx, fy + fh, 0));
        DrawLine(color, thickness, Vector3(fx + fw - 1, fy + fh - ih, 0), Vector3(fx + fw - 1, fy + fh, 0));
    }

    void DrawBox4Line(Rect rect, Color color, float thickness) {
        DrawBox4Line((int)rect.x, (int)rect.y, (int)rect.width, (int)rect.height, color, thickness);
    }

    void DrawVerticalHealthBar(Vector2 screenPos, float height, float maxHealth,
                               float currentHealth, float width = 8.0f, bool showText = false) {
        if (maxHealth <= 0.0f) maxHealth = 200.0f;
        float healthPercentage = std::min(1.0f, std::max(0.0f, currentHealth / maxHealth));
        float healthHeight = height * healthPercentage;

        Color healthColor;
        if (healthPercentage > 0.6f) {
            healthColor = Color(0, 255, 0, 200);
        } else if (healthPercentage > 0.3f) {
            healthColor = Color(255, 255, 0, 200);
        } else {
            healthColor = Color(255, 0, 0, 200);
        }

        float healthY = screenPos.Y + (height - healthHeight);
        DrawFilledRect(healthColor, Vector3(screenPos.X, healthY, 0), width, healthHeight);

        if (showText && height > 30) {
            char healthText[16];
            snprintf(healthText, sizeof(healthText), "%.0f", currentHealth);
            DrawText(Color(255, 255, 255, 200),
                     healthText,
                     Vector2(screenPos.X - 25, screenPos.Y + (height / 2) - 6),
                     10.0f);
        }
    }

    void DrawHorizontalHealthBar(Vector2 screenPos, float width, float maxHealth, float currentHealth) {
        if (maxHealth <= 0.0f) maxHealth = 200.0f;
        screenPos -= Vector2(0.0f, 5.0f);
        DrawBox(Color(0, 0, 0, 255), 1, Rect(screenPos.X, screenPos.Y, width, 5.0f));
        screenPos += Vector2(1.0f, 1.0f);
        Color clr = Color(0, 255, 0, 255);
        float hpWidth = (currentHealth * (width - 2)) / maxHealth;
        hpWidth = std::min(hpWidth, width);

        if (currentHealth <= (maxHealth * 0.6f)) {
            clr = Color(255, 255, 0, 255);
        }
        if (currentHealth < (maxHealth * 0.4f)) {
            clr = Color(255, 0, 0, 255);
        }
        DrawBox(clr, 1, Rect(screenPos.X, screenPos.Y, hpWidth, 4.0f));
    }
};

#endif