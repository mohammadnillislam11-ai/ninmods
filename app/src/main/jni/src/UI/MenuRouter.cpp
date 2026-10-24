#include "MenuRouter.h"
#include "Brands/NinMenu.h"
#include "Brands/BullMenu.h"
#include "Brands/CrossMenu.h"
#include "Brands/DeltaMenu.h"

std::unordered_map<std::string, std::unique_ptr<IBrandMenu>> MenuRouter::s_BrandRegistry;
IBrandMenu* MenuRouter::s_ActiveMenu = nullptr;
bool MenuRouter::s_Initialized = false;

void MenuRouter::Initialize() {
    if (s_Initialized) return;

    RegisterBrand(std::make_unique<NinMenu>());
    RegisterBrand(std::make_unique<BullMenu>());
    RegisterBrand(std::make_unique<CrossMenu>());
    RegisterBrand(std::make_unique<DeltaMenu>());

    // Default to Nin
    s_ActiveMenu = s_BrandRegistry["nin"].get();
    s_Initialized = true;
}

void MenuRouter::RegisterBrand(std::unique_ptr<IBrandMenu> menu) {
    if (!menu) return;
    std::string id = menu->GetId();
    s_BrandRegistry[id] = std::move(menu);
}

bool MenuRouter::SelectBrand(const std::string& brandId) {
    Initialize();
    auto it = s_BrandRegistry.find(brandId);
    if (it != s_BrandRegistry.end()) {
        s_ActiveMenu = it->second.get();
        return true;
    }
    // Fallback to Nin if brand not found
    s_ActiveMenu = s_BrandRegistry["nin"].get();
    return false;
}

IBrandMenu* MenuRouter::GetActiveMenu() {
    Initialize();
    return s_ActiveMenu;
}

IBrandMenu* MenuRouter::GetBrand(const std::string& brandId) {
    Initialize();
    auto it = s_BrandRegistry.find(brandId);
    if (it != s_BrandRegistry.end()) {
        return it->second.get();
    }
    return nullptr;
}

const std::unordered_map<std::string, std::unique_ptr<IBrandMenu>>& MenuRouter::GetAllBrands() {
    Initialize();
    return s_BrandRegistry;
}
