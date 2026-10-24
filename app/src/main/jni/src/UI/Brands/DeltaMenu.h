#pragma once

#include "../IBrandMenu.h"

class DeltaMenu : public IBrandMenu {
public:
    const char* GetId() const override { return "delta"; }
    const char* GetDisplayName() const override { return "DELTA STREAMLINE"; }
    const char* GetSubtitle() const override { return "Ultra Speed Edition"; }

    ImVec4 GetPrimaryColor() const override { return ImVec4(0.0f, 0.70f, 1.0f, 1.0f); } // #00B0FF Electric Blue
    ImVec4 GetBackgroundColor() const override { return ImVec4(0.04f, 0.06f, 0.12f, 0.95f); } // Deep Space Blue

    void ApplyTheme() override;
    void RenderMenu(bool& isOpen, bool isKernelActive) override;
};
