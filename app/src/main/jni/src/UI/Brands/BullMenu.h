#pragma once

#include "../IBrandMenu.h"

class BullMenu : public IBrandMenu {
public:
    const char* GetId() const override { return "bull"; }
    const char* GetDisplayName() const override { return "BULL VIP TACTICAL"; }
    const char* GetSubtitle() const override { return "Aggressive Pro Edition"; }

    ImVec4 GetPrimaryColor() const override { return ImVec4(1.0f, 0.70f, 0.0f, 1.0f); } // #FFB300 Golden Amber
    ImVec4 GetBackgroundColor() const override { return ImVec4(0.10f, 0.06f, 0.04f, 0.95f); } // Deep Warm Black/Amber

    void ApplyTheme() override;
    void RenderMenu(bool& isOpen, bool isKernelActive) override;
};
