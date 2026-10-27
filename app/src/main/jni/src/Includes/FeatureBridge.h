#pragma once

/**
 * SHARED FEATURE BRIDGE
 * ---------------------
 * Every write a menu performs on the engine goes through these four
 * functions.
 *
 * The classic Java menu (NativeBridge.setFeatureState / setFeatureFloat /
 * getFeatureState / getFeatureFloat) and the reference parity DSL menu
 * (Menu/Preferences.cpp -> Changes) both call them, so there is exactly ONE
 * switch that maps a feature id onto an engine flag. A second, parallel
 * switch would inevitably drift away from the first one and two menus would
 * drive different flags with the same id.
 *
 * The feature id is the SAME number in the menu DSL string, in the Java
 * FeatureCatalog and in these switches - one numbering space for the whole
 * project.
 */
void NinFeatureSetState(int featureId, bool state);

void NinFeatureSetValue(int featureId, float value);

bool NinFeatureGetState(int featureId);

float NinFeatureGetValue(int featureId);
