#pragma once

#include "IBrandMenu.h"
#include <unordered_map>
#include <memory>
#include <string>

/**
 * Central C++ Menu Router & Brand Registry.
 * Handles instant dynamic switching between brand menus without any JNI bridging.
 */
class MenuRouter {
private:
    static std::unordered_map<std::string, std::unique_ptr<IBrandMenu>> s_BrandRegistry;
    static IBrandMenu* s_ActiveMenu;
    static bool s_Initialized;

public:
    static void Initialize();
    static void RegisterBrand(std::unique_ptr<IBrandMenu> menu);
    static bool SelectBrand(const std::string& brandId);
    static IBrandMenu* GetActiveMenu();
    static IBrandMenu* GetBrand(const std::string& brandId);
    static const std::unordered_map<std::string, std::unique_ptr<IBrandMenu>>& GetAllBrands();
};
