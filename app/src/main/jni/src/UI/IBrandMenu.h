#pragma once

#include <imgui.h>
#include <string>

/**
 * Universal Master Contract for Modular Tactical Brand Menus in C++.
 * Enforces strict isolation: each brand (Nin, Bull, Cross, Delta) has its own
 * dedicated file, distinct theme styling, and customized UI layout.
 */
class IBrandMenu {
public:
    virtual ~IBrandMenu() = default;

    // Brand Identification
    virtual const char* GetId() const = 0;              // e.g. "nin", "bull", "cross", "delta"
    virtual const char* GetDisplayName() const = 0;     // e.g. "NIN TACTICAL", "BULL VIP"
    virtual const char* GetSubtitle() const = 0;        // e.g. "Ring 0 Kernel Edition", "Pro V90"

    // Dynamic Theming & Colors
    virtual ImVec4 GetPrimaryColor() const = 0;         // Neon brand accent color
    virtual ImVec4 GetBackgroundColor() const = 0;      // Background tint
    virtual void ApplyTheme() = 0;                      // Custom ImGui style for this specific brand

    // Core Tactical UI Rendering
    // isKernelActive: true if /dev/BYNb is live, false if in Preview / Non-Root mode
    virtual void RenderMenu(bool& isOpen, bool isKernelActive) = 0;
};
