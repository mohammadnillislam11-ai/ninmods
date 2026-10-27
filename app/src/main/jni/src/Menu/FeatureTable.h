#pragma once

#include <string>
#include <vector>

/**
 * NINMODS MOD MENU FEATURE TABLE
 * -------------------------------
 * This table is the native equivalent of the `features[]` array of the
 * reference mod menu. It is turned into the reference feature DSL
 *
 *     (<index>)_Toggle_<name>
 *     (<index>)_True_Toggle_<name>
 *     (<index>)_SeekBar_<name>_<min>_<max>
 *
 * and the Java layer rebuilds the complete menu from that string array, so
 * the native side stays the single owner of the menu structure - the Java
 * side only renders it.
 *
 * The number in front of the DSL entry IS the engine feature id, therefore
 * there is only one id space in the whole project (Java FeatureCatalog,
 * the C++ switches in Client.cpp and this table all use the same numbers).
 *
 * The label of a feature must not contain an underscore: the reference DSL
 * parser splits the row on "_" and uses the second token as the visible
 * name.
 */
namespace NinMenuDsl {

enum FeatureType {
    FEATURE_SWITCH = 0,
    FEATURE_SLIDER = 1,
    FEATURE_SPINNER = 2,
};

struct FeatureDef {
    int id;                 // engine feature id
    int type;               // FEATURE_SWITCH | FEATURE_SLIDER | FEATURE_SPINNER
    const char *label;      // visible name (no underscore allowed)
    bool trueByDefault;     // emits the "_True" marker of the reference DSL
    int minValue;           // sliders only
    int maxValue;           // sliders only
    const char *group;      // category header this row belongs to
    const char *options;    // spinners only: "a,b,c"
};

/**
 * The complete menu catalogue.
 *
 * The rows are the options of the reference engine: the box,
 * the line, the health bar, the name, the distance, the weapon, the threat
 * counter, the info line and the aim options with the head and chest rate.
 * The options the reference engine does not render (skeleton, radar) are
 * not in the menu any more - a switch without a renderer is a lie.
 *
 * The default state of every row matches the default state of the engine
 * flag behind it, so a freshly opened menu never shows a value that the
 * engine does not actually have. Every real cheat stays OFF until the user
 * switches it on (rule 2: 100% manual control).
 */
inline const std::vector<FeatureDef> &FeatureCatalogue() {
    static const std::vector<FeatureDef> catalogue = {
            /* ---------------- VISUALS ---------------- */
            {1, FEATURE_SWITCH, "ESP MASTER SWITCH", false, 0, 0, "VISUALS", ""},
            {2, FEATURE_SWITCH, "ESP BOX (2D)", false, 0, 0, "VISUALS", ""},
            {4, FEATURE_SWITCH, "ESP LINE (TRACER)", false, 0, 0, "VISUALS", ""},
            {5, FEATURE_SWITCH, "ESP HEALTH BAR", false, 0, 0, "VISUALS", ""},
            {6, FEATURE_SWITCH, "ESP PLAYER NAME", false, 0, 0, "VISUALS", ""},
            {7, FEATURE_SWITCH, "ESP DISTANCE IN METERS", false, 0, 0, "VISUALS", ""},
            {206, FEATURE_SWITCH, "ESP WEAPON NAME", false, 0, 0, "VISUALS", ""},
            {207, FEATURE_SWITCH, "THREAT COUNTER", false, 0, 0, "VISUALS", ""},
            {208, FEATURE_SWITCH, "ESP INFO LINE", true, 0, 0, "VISUALS", ""},
            {27, FEATURE_SWITCH, "DRAW FOV CIRCLE", true, 0, 0, "VISUALS", ""},
            {201, FEATURE_SPINNER, "ESP BOX STYLE", false, 0, 0, "VISUALS", "STROKE,FILLED,CORNER,ROUNDED"},
            {202, FEATURE_SPINNER, "ESP LINE SNAP MODE", false, 0, 0, "VISUALS", "TOP,CENTER,BOTTOM"},
            {209, FEATURE_SLIDER, "ESP TEXT SIZE", false, 8, 24, "VISUALS", ""},

            /* ---------------- AIMBOT ---------------- */
            {20, FEATURE_SWITCH, "AIM HARD (REAL AIM)", false, 0, 0, "AIMBOT", ""},
            {21, FEATURE_SWITCH, "AIM COLLIDER LOCK", false, 0, 0, "AIMBOT", ""},
            {22, FEATURE_SWITCH, "SILENT AIM (MAGIC BULLET)", false, 0, 0, "AIMBOT", ""},
            {23, FEATURE_SWITCH, "AIM MAGNET V2", false, 0, 0, "AIMBOT", ""},
            {24, FEATURE_SWITCH, "IGNORE KNOCKED PLAYERS", true, 0, 0, "AIMBOT", ""},
            {214, FEATURE_SLIDER, "HEAD RATE", false, 0, 3, "AIMBOT", ""},
            {215, FEATURE_SLIDER, "CHEST RATE", false, 0, 6, "AIMBOT", ""},
            {216, FEATURE_SWITCH, "KILL IN CHEST", false, 0, 0, "AIMBOT", ""},
            {303, FEATURE_SLIDER, "AIM FOV ANGLE", false, 10, 180, "AIMBOT", ""},
            {101, FEATURE_SLIDER, "MAX TARGET DISTANCE", false, 10, 350, "AIMBOT", ""},

            /* ---------------- COMBAT ---------------- */
            {40, FEATURE_SWITCH, "NO RECOIL (100% STABLE)", false, 0, 0, "COMBAT", ""},
            {41, FEATURE_SWITCH, "SPEED MOVEMENT (2X)", false, 0, 0, "COMBAT", ""},
            {42, FEATURE_SWITCH, "PLAYER TO ME (TELEPORT)", false, 0, 0, "COMBAT", ""},
            {43, FEATURE_SWITCH, "UNDERGROUND CAMERA", false, 0, 0, "COMBAT", ""},
            {44, FEATURE_SWITCH, "ANTICHEAT REALTIME BYPASS", false, 0, 0, "COMBAT", ""},
            {102, FEATURE_SLIDER, "GAME SPEED MULTIPLIER", false, 1, 5, "COMBAT", ""},
    };
    return catalogue;
}

inline const FeatureDef *FindFeature(int id) {
    const std::vector<FeatureDef> &catalogue = FeatureCatalogue();
    for (size_t i = 0; i < catalogue.size(); i++) {
        if (catalogue[i].id == id) {
            return &catalogue[i];
        }
    }
    return nullptr;
}

/**
 * Turns the catalogue into the reference feature DSL.
 * A category header is emitted whenever the group of a row changes.
 */
inline std::vector<std::string> BuildFeatureDsl() {
    std::vector<std::string> rows;
    const std::vector<FeatureDef> &catalogue = FeatureCatalogue();
    std::string currentGroup;

    for (size_t i = 0; i < catalogue.size(); i++) {
        const FeatureDef &feature = catalogue[i];

        if (currentGroup != feature.group) {
            currentGroup = feature.group;
            rows.push_back(std::string("Category_") + currentGroup);
        }

        std::string row = std::to_string(feature.id);
        if (feature.type == FEATURE_SLIDER) {
            row += "_SeekBar_";
            row += feature.label;
            row += "_" + std::to_string(feature.minValue);
            row += "_" + std::to_string(feature.maxValue);
        } else if (feature.type == FEATURE_SPINNER) {
            row += "_Spinner_";
            row += feature.label;
            row += "_";
            row += feature.options;
        } else {
            if (feature.trueByDefault) {
                row += "_True";
            }
            row += "_Toggle_";
            row += feature.label;
        }
        rows.push_back(row);
    }

    return rows;
}

}  // namespace NinMenuDsl
