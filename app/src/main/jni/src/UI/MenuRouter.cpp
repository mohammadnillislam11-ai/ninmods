#include "MenuRouter.h"
#include "Brands/NinMenu.h"
#include "Brands/BullMenu.h"
#include "Brands/CrossMenu.h"
#include "Brands/DeltaMenu.h"

std::unordered_map<std::string, std::unique_ptr<IBrandMenu>> MenuRouter::s_BrandRegistry;
IBrandMenu* MenuRouter::s_ActiveMenu = nullptr;
bool MenuRouter::s_Initialized = false;
std::once_flag MenuRouter::s_InitOnce;
std::mutex MenuRouter::s_Mutex;

void MenuRouter::Initialize() {
    /* std::call_once guarantees that the registry is built EXACTLY once even
     * when the GL thread and the JNI thread race into this function. The old
     * plain bool check let both threads through, and the second registration
     * freed the menu object that s_ActiveMenu was still pointing at. */
    std::call_once(s_InitOnce, []() {
        s_BrandRegistry["nin"] = std::make_unique<NinMenu>();
        s_BrandRegistry["bull"] = std::make_unique<BullMenu>();
        s_BrandRegistry["cross"] = std::make_unique<CrossMenu>();
        s_BrandRegistry["delta"] = std::make_unique<DeltaMenu>();

        auto it = s_BrandRegistry.find("nin");
        s_ActiveMenu = (it != s_BrandRegistry.end()) ? it->second.get() : nullptr;
        s_Initialized = true;
    });
}

void MenuRouter::RegisterBrand(std::unique_ptr<IBrandMenu> menu) {
    if (!menu) return;
    std::lock_guard<std::mutex> lock(s_Mutex);
    std::string id = menu->GetId();
    s_BrandRegistry[id] = std::move(menu);
}

bool MenuRouter::SelectBrand(const std::string& brandId) {
    Initialize();
    std::lock_guard<std::mutex> lock(s_Mutex);
    auto it = s_BrandRegistry.find(brandId);
    if (it != s_BrandRegistry.end()) {
        s_ActiveMenu = it->second.get();
        return true;
    }
    /* Fallback to Nin if brand not found.
     * find() is used instead of operator[] because operator[] INSERTS a null
     * entry for an unknown key and could leave s_ActiveMenu dangling. */
    auto nin = s_BrandRegistry.find("nin");
    s_ActiveMenu = (nin != s_BrandRegistry.end()) ? nin->second.get() : nullptr;
    return false;
}

IBrandMenu* MenuRouter::GetActiveMenu() {
    Initialize();
    std::lock_guard<std::mutex> lock(s_Mutex);
    return s_ActiveMenu;
}

IBrandMenu* MenuRouter::GetBrand(const std::string& brandId) {
    Initialize();
    std::lock_guard<std::mutex> lock(s_Mutex);
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
