#pragma once

#include "../IBrandMenu.h"

class NinMenu : public IBrandMenu {
public:
    const char* GetId() const override { return "nin"; }
    const char* GetDisplayName() const override { return "NIN TACTICAL"; }
    const char* GetSubtitle() const override { return "Ring 0 Kernel Edition"; }

    ImVec4 GetPrimaryColor() const override { return ImVec4(0.91f, 0.12f, 0.39f, 1.0f); } // #E91E63 Neon Magenta
    ImVec4 GetBackgroundColor() const override { return ImVec4(0.08f, 0.05f, 0.10f, 0.94f); } // Dark Cyber Tint

    void ApplyTheme() override;
    void RenderMenu(bool& isOpen, bool isKernelActive) override;
};
