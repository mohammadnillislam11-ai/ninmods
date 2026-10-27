#pragma once

#include "IBrandMenu.h"
#include <unordered_map>
#include <memory>
#include <mutex>
#include <string>

/**
 * Central C++ Menu Router & Brand Registry.
 * Handles instant dynamic switching between brand menus without any JNI bridging.
 *
 * THREAD SAFETY: Initialize() is reachable from the GL render thread
 * (nativeInit) AND from the JNI thread (launchBrandOverlay). Two racing
 * callers both passed the old `if (s_Initialized) return;` check and the
 * second one replaced the registered menus, freeing the object that
 * s_ActiveMenu still pointed at -> use after free. The registry is now built
 * exactly once (std::once_flag) and every accessor is mutex protected.
 */
class MenuRouter {
private:
    static std::unordered_map<std::string, std::unique_ptr<IBrandMenu>> s_BrandRegistry;
    static IBrandMenu* s_ActiveMenu;
    static bool s_Initialized;
    static std::once_flag s_InitOnce;
    static std::mutex s_Mutex;

public:
    static void Initialize();
    static void RegisterBrand(std::unique_ptr<IBrandMenu> menu);
    static bool SelectBrand(const std::string& brandId);
    static IBrandMenu* GetActiveMenu();
    static IBrandMenu* GetBrand(const std::string& brandId);
    static const std::unordered_map<std::string, std::unique_ptr<IBrandMenu>>& GetAllBrands();
};
