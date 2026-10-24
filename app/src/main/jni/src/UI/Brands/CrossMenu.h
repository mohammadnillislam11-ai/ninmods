#pragma once

#include "../IBrandMenu.h"

class CrossMenu : public IBrandMenu {
public:
    const char* GetId() const override { return "cross"; }
    const char* GetDisplayName() const override { return "CROSS TARGET"; }
    const char* GetSubtitle() const override { return "Stealth Tactical Edition"; }

    ImVec4 GetPrimaryColor() const override { return ImVec4(0.0f, 0.90f, 0.46f, 1.0f); } // #00E676 Emerald Green
    ImVec4 GetBackgroundColor() const override { return ImVec4(0.04f, 0.08f, 0.06f, 0.95f); } // Deep Tactical Green/Black

    void ApplyTheme() override;
    void RenderMenu(bool& isOpen, bool isKernelActive) override;
};
