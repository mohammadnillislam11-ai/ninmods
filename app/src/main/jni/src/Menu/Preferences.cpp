#include "Menu/Menu.hpp"
#include "Menu/FeatureTable.h"
#include "Includes/FeatureBridge.h"
#include "Includes/Logger.h"
#include "Includes/obfuscate.h"

/*
 * THE SINGLE CALLBACK OF THE MENU
 * --------------------------------
 * The Java layer never touches the engine. It stores the value of a row and
 * forwards it here, and this function is the only place where a menu value
 * reaches the engine - exactly like the reference mod menu, where the
 * native `Changes` callback is the only writer.
 *
 * Signature (identical to the reference):
 *   Changes(Context, int featNum, String featName, int value, long Lvalue,
 *           boolean isOn, String inputText)
 *
 * The slot that carries the meaningful value depends on the type of the
 * control: a switch sends the boolean, a slider / spinner / radio button
 * sends the int, an input field sends the long or the text.
 */
void Changes(JNIEnv *env, jclass clazz, jobject obj, jint featNum, jstring featName, jint value,
             jlong Lvalue, jboolean boolean, jstring text) {
    (void) env;
    (void) clazz;
    (void) obj;
    (void) featName;
    (void) Lvalue;
    (void) text;

    const NinMenuDsl::FeatureDef *feature = NinMenuDsl::FindFeature(static_cast<int>(featNum));
    if (feature == nullptr) {
        /*
         * Everything that is not in the feature table is a reserved settings
         * row of the reference system (-1 save preferences, -3 auto size,
         * -6 close settings, -100 force load). Those are handled completely
         * on the Java side and must not touch the engine.
         */
        return;
    }

    if (feature->type == NinMenuDsl::FEATURE_SWITCH) {
        NinFeatureSetState(static_cast<int>(featNum), (boolean == JNI_TRUE));
        return;
    }

    /*
     * Sliders are stored as int. A stored value of 0 means "the user never
     * touched this control", so it must never be written into the engine -
     * that would push a value below the minimum of the control.
     */
    int intValue = static_cast<int>(value);
    if (intValue == 0 && feature->minValue > 0) {
        return;
    }
    NinFeatureSetValue(static_cast<int>(featNum), static_cast<float>(intValue));
}
