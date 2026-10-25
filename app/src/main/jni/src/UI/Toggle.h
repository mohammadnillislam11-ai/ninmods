#pragma once
#include <atomic>
#include <map>
#include <cstring>
#include <algorithm>
#include "imgui.h"
#include "imgui_internal.h"

inline std::map<ImGuiID, float> s_toggle_anim_state;

/**
 * Modern High-Performance Animated Cyber-Pill Toggle.
 * Adapted directly from NINMODS original UI architecture.
 */
inline bool Toggle(const char* label, bool* state) {
    if (!state) return false;
    ImGuiWindow* window = ImGui::GetCurrentWindow();
    if (window->SkipItems) return false;

    ImGuiContext& g = *GImGui;
    const ImGuiStyle& style = g.Style;
    const ImGuiID id = window->GetID(label);
    ImVec2 label_size = ImGui::CalcTextSize(label, NULL, true);

    const float font_h = label_size.y > 0.0f ? label_size.y : ImGui::GetFontSize();
    const float toggle_height = ImMax(24.0f, font_h * 1.05f);
    const float toggle_width = toggle_height * 1.84f;
    const float radius = toggle_height * 0.5f;
    const float vertical_margin = 4.0f;
    const float total_widget_height = ImMax(toggle_height, label_size.y) + vertical_margin * 2.0f;
    const float total_width = ImGui::GetContentRegionAvail().x;
    const ImRect bb(window->DC.CursorPos, ImVec2(window->DC.CursorPos.x + total_width, window->DC.CursorPos.y + total_widget_height));

    ImGui::ItemSize(bb, style.FramePadding.y);
    if (!ImGui::ItemAdd(bb, id)) return false;

    bool hovered = false, held = false;
    bool pressed = ImGui::ButtonBehavior(bb, id, &hovered, &held);

    if (pressed && !ImGui::IsMouseDragging(0, 6.0f)) {
        *state = !*state;
        ImGui::MarkItemEdited(id);
    }

    float target_state = *state ? 1.0f : 0.0f;
    if (s_toggle_anim_state.find(id) == s_toggle_anim_state.end()) s_toggle_anim_state[id] = target_state;
    s_toggle_anim_state[id] = ImClamp(ImLerp(s_toggle_anim_state[id], target_state, g.IO.DeltaTime * 14.0f), 0.0f, 1.0f);
    float current_anim_state = s_toggle_anim_state[id];

    ImVec4 c_off_bg(0.20f, 0.26f, 0.36f, 0.65f), c_on_bg(0.12f, 0.52f, 0.98f, 0.92f);
    ImVec4 c_off_border(0.55f, 0.70f, 0.90f, 0.40f), c_on_border(0.80f, 0.92f, 1.00f, 0.85f);
    ImVec4 c_off_knob(0.94f, 0.96f, 0.98f, 1.00f), c_on_knob(1.00f, 1.00f, 1.00f, 1.00f);

    ImU32 bg_color     = ImGui::ColorConvertFloat4ToU32(ImLerp(c_off_bg, c_on_bg, current_anim_state));
    ImU32 border_color = ImGui::ColorConvertFloat4ToU32(ImLerp(c_off_border, c_on_border, current_anim_state));
    ImU32 knob_color   = ImGui::ColorConvertFloat4ToU32(ImLerp(c_off_knob, c_on_knob, current_anim_state));

    float centered_y = bb.Min.y + vertical_margin;
    ImVec2 text_pos(bb.Min.x + 4.0f, centered_y + (toggle_height - label_size.y) * 0.5f);
    window->DrawList->AddText(text_pos, ImGui::GetColorU32(ImGuiCol_Text), label);

    ImVec2 toggle_pos(bb.Max.x - toggle_width - 4.0f, centered_y);
    ImVec2 toggle_max(toggle_pos.x + toggle_width, toggle_pos.y + toggle_height);
    window->DrawList->AddRectFilled(toggle_pos, toggle_max, bg_color, radius);
    window->DrawList->AddRect(toggle_pos, toggle_max, border_color, radius, 0, 1.0f);

    const float knob_inset = 2.0f, knob_radius = (toggle_height - knob_inset * 2.0f) * 0.5f;
    const float track_travel = toggle_width - toggle_height;
    ImVec2 knob_center(toggle_pos.x + radius + current_anim_state * track_travel, toggle_pos.y + radius);

    window->DrawList->AddCircleFilled(ImVec2(knob_center.x, knob_center.y + 1.2f), knob_radius, IM_COL32(0, 0, 0, 60));
    window->DrawList->AddCircleFilled(knob_center, knob_radius, knob_color);
    return pressed;
}

inline bool Toggle(const char* label, std::atomic<bool>* state) {
    if (!state) return false;
    bool val = state->load();
    if (Toggle(label, &val)) {
        state->store(val);
        return true;
    }
    return false;
}

inline bool SelectOptionRow(const char* label, bool selected) {
    ImGuiWindow* window = ImGui::GetCurrentWindow();
    if (window->SkipItems) return false;

    ImGuiContext& g = *GImGui;
    const ImGuiStyle& style = g.Style;
    const ImGuiID id = window->GetID(label);
    ImVec2 label_size = ImGui::CalcTextSize(label, NULL, true);

    const float font_h = label_size.y > 0.0f ? label_size.y : ImGui::GetFontSize();
    const float widget_height = ImMax(25.0f, font_h * 1.05f);
    const float vertical_margin = 3.0f;
    const float total_widget_height = ImMax(widget_height, label_size.y) + vertical_margin * 2.0f;
    const float total_width = ImGui::GetContentRegionAvail().x;
    const ImRect bb(window->DC.CursorPos, ImVec2(window->DC.CursorPos.x + total_width, window->DC.CursorPos.y + total_widget_height));

    ImGui::ItemSize(bb, style.FramePadding.y);
    if (!ImGui::ItemAdd(bb, id)) return false;

    bool hovered = false, held = false;
    bool pressed = ImGui::ButtonBehavior(bb, id, &hovered, &held);
    if (pressed && ImGui::IsMouseDragging(0, 6.0f)) pressed = false;

    float centered_y = bb.Min.y + vertical_margin;
    ImVec2 text_pos(bb.Min.x + 4.0f, centered_y + (widget_height - label_size.y) * 0.5f);
    ImU32 text_col = ImGui::GetColorU32(ImGuiCol_Text);
    window->DrawList->AddText(text_pos, text_col, label);

    const float box_size = ImMax(22.0f, font_h * 0.90f);
    ImVec2 b_min(bb.Max.x - box_size - 4.0f, centered_y + (widget_height - box_size) * 0.5f);
    ImVec2 b_max(b_min.x + box_size, b_min.y + box_size);

    ImU32 bg_col = selected ? IM_COL32(28, 140, 255, 235) : IM_COL32(28, 38, 52, 160);
    ImU32 brd_col = selected ? IM_COL32(180, 220, 255, 230) : IM_COL32(75, 95, 130, 120);

    window->DrawList->AddRectFilled(b_min, b_max, bg_col, 5.0f);
    window->DrawList->AddRect(b_min, b_max, brd_col, 5.0f, 0, 1.2f);

    if (selected) {
        float b_scale = box_size / 22.0f;
        ImVec2 p1(b_min.x + 5.0f * b_scale, b_min.y + 11.0f * b_scale);
        ImVec2 p2(b_min.x + 9.0f * b_scale, b_min.y + 15.5f * b_scale);
        ImVec2 p3(b_min.x + 16.5f * b_scale, b_min.y + 6.5f * b_scale);
        window->DrawList->AddLine(p1, p2, IM_COL32(255, 255, 255, 255), 2.2f * b_scale);
        window->DrawList->AddLine(p2, p3, IM_COL32(255, 255, 255, 255), 2.2f * b_scale);
    }
    return pressed;
}

inline bool MobileSliderFloat(const char* label, float* v, float v_min, float v_max, const char* format = "%.1f") {
    if (!v) return false;
    ImGuiWindow* window = ImGui::GetCurrentWindow();
    if (window->SkipItems) return false;

    ImGuiContext& g = *GImGui;
    const ImGuiStyle& style = g.Style;
    const ImGuiID id = window->GetID(label);
    const float avail_w = ImGui::GetContentRegionAvail().x;
    const float widget_h = ImMax(28.0f, ImGui::GetFontSize() * 1.20f);
    const ImVec2 pos = window->DC.CursorPos;
    const ImRect bb(pos, ImVec2(pos.x + avail_w, pos.y + widget_h));

    ImGui::ItemSize(bb, style.FramePadding.y);
    if (!ImGui::ItemAdd(bb, id)) return false;

    bool value_changed = false;
    bool hovered = false, held = false;
    ImGui::ButtonBehavior(bb, id, &hovered, &held, ImGuiButtonFlags_PressedOnClick);

    const float thumb_radius = ImMax(9.0f, widget_h * 0.32f);
    const float track_h = ImMax(6.0f, widget_h * 0.22f);
    const float track_pad_x = thumb_radius + 2.0f;
    const float track_x0 = bb.Min.x + track_pad_x, track_x1 = bb.Max.x - track_pad_x;
    const float track_w  = track_x1 - track_x0, track_y = bb.Min.y + widget_h * 0.5f;

    if (held && track_w > 0.0f) {
        float norm = ImClamp((g.IO.MousePos.x - track_x0) / track_w, 0.0f, 1.0f);
        float new_v = v_min + norm * (v_max - v_min);
        if (*v != new_v) { *v = new_v; value_changed = true; ImGui::MarkItemEdited(id); }
    }

    float norm_val = (v_max > v_min) ? ImClamp((*v - v_min) / (v_max - v_min), 0.0f, 1.0f) : 0.0f;
    float thumb_x  = track_x0 + norm_val * track_w;

    ImVec2 t0(track_x0, track_y - track_h * 0.5f), t1(track_x1, track_y + track_h * 0.5f);
    window->DrawList->AddRectFilled(t0, t1, IM_COL32(45, 60, 85, 170), track_h * 0.5f);
    window->DrawList->AddRect(t0, t1, IM_COL32(85, 115, 160, 90), track_h * 0.5f, 0, 1.0f);

    if (thumb_x > track_x0) {
        ImVec2 t_act(thumb_x, track_y + track_h * 0.5f);
        window->DrawList->AddRectFilled(t0, t_act, IM_COL32(28, 140, 255, 235), track_h * 0.5f);
    }

    window->DrawList->AddCircleFilled(ImVec2(thumb_x, track_y + 1.2f), thumb_radius, IM_COL32(0, 0, 0, 80));
    window->DrawList->AddCircleFilled(ImVec2(thumb_x, track_y), thumb_radius, IM_COL32(250, 252, 255, 255));
    window->DrawList->AddCircle(ImVec2(thumb_x, track_y), thumb_radius, IM_COL32(180, 215, 255, 200), 0, 1.2f);
    return value_changed;
}
