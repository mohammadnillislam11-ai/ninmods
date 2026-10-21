void DrawESP(ESP esp) {
    auto screenHeight = esp.getHeight();
    auto screenWidth = esp.getWidth();
    if (!isConnected()) return;

    auto response = getData(screenWidth, screenHeight);
    if (!response.Success) return;

    if (Actived.activar && pEspPlayer.espDrawCount) {
        const bool showFirstSection = response.VisibleEnemyCount > 0;
        const bool showSecondSection = response.VisibleNearOffScreenEnemyCount > 0;
        const bool noThreatVisible = !showFirstSection && !showSecondSection;
        std::string visibleCountLabel = std::to_string(response.VisibleEnemyCount);
        std::string nearOffScreenCountLabel = std::to_string(response.VisibleNearOffScreenEnemyCount);

        float countSize = pEspPlayer.textSize + 10.0f;
        if (countSize < 24.0f) countSize = 24.0f;

        float topMargin = 80.0f;
        float sectionWidth = 120.0f;
        float sectionHeight = countSize + 22.0f;
        float sectionGap = 14.0f;
        float totalWidth = showFirstSection && showSecondSection ? ((sectionWidth * 2.0f) + sectionGap) : sectionWidth;
        float startX = (screenWidth - totalWidth) / 2.0f;
        
        // Fix: Center text vertically within the panel
        float backgroundY = topMargin;  // background top edge
        float countY = backgroundY + sectionHeight / 2.0f;  // vertical center of panel

        float cornerRadius = sectionHeight * 0.30f;
        Color outlineColor = Color(0, 0, 0, 220);
        Color textColor = Color::White();

        auto DrawRoundedPanel = [&](float x, float y, float width, float height, float radius, Color color) {
            float clampedRadius = std::max(1.0f, std::min(radius, std::min(width, height) * 0.5f));
            esp.DrawFilledRect(color, Vector3(x + clampedRadius, y, 0), width - (clampedRadius * 2.0f), height);
            esp.DrawFilledRect(color, Vector3(x, y + clampedRadius, 0), clampedRadius, height - (clampedRadius * 2.0f));
            esp.DrawFilledRect(color, Vector3(x + width - clampedRadius, y + clampedRadius, 0), clampedRadius, height - (clampedRadius * 2.0f));
            esp.DrawFilledCircle(color, Vector2(x + clampedRadius, y + clampedRadius), clampedRadius);
            esp.DrawFilledCircle(color, Vector2(x + width - clampedRadius, y + clampedRadius), clampedRadius);
            esp.DrawFilledCircle(color, Vector2(x + clampedRadius, y + height - clampedRadius), clampedRadius);
            esp.DrawFilledCircle(color, Vector2(x + width - clampedRadius, y + height - clampedRadius), clampedRadius);
        };

        if (noThreatVisible) {
            float safeWidth = 200.0f;
            float safeX = (screenWidth - safeWidth) / 2.0f;
            DrawRoundedPanel(safeX, backgroundY, safeWidth, sectionHeight, cornerRadius, Color(22, 165, 70, 235));
            const char* safeLabel = "SAFE";
            float safeTextX = safeX + (safeWidth * 0.5f);
            // Use same countY (vertical center) for the text
            esp.DrawText(outlineColor, safeLabel, Vector2(safeTextX - 1, countY - 1), countSize - 2.0f);
            esp.DrawText(outlineColor, safeLabel, Vector2(safeTextX + 1, countY + 1), countSize - 2.0f);
            esp.DrawText(textColor, safeLabel, Vector2(safeTextX, countY), countSize - 2.0f);
        } else {
            Color firstSectionBg = Color(255, 215, 0, 230);
            Color secondSectionBg = Color(235, 30, 30, 230);
            float nextSectionX = startX;

            if (showFirstSection) {
                DrawRoundedPanel(nextSectionX, backgroundY, sectionWidth, sectionHeight, cornerRadius, firstSectionBg);
                float firstCountX = nextSectionX + (sectionWidth * 0.5f);
                esp.DrawText(outlineColor, visibleCountLabel.c_str(), Vector2(firstCountX - 1, countY - 1), countSize);
                esp.DrawText(outlineColor, visibleCountLabel.c_str(), Vector2(firstCountX + 1, countY + 1), countSize);
                esp.DrawText(textColor, visibleCountLabel.c_str(), Vector2(firstCountX, countY), countSize);
                nextSectionX += sectionWidth + sectionGap;
            }

            if (showSecondSection) {
                DrawRoundedPanel(nextSectionX, backgroundY, sectionWidth, sectionHeight, cornerRadius, secondSectionBg);
                float secondCountX = nextSectionX + (sectionWidth * 0.5f);
                esp.DrawText(outlineColor, nearOffScreenCountLabel.c_str(), Vector2(secondCountX - 1, countY - 1), countSize);
                esp.DrawText(outlineColor, nearOffScreenCountLabel.c_str(), Vector2(secondCountX + 1, countY + 1), countSize);
                esp.DrawText(textColor, nearOffScreenCountLabel.c_str(), Vector2(secondCountX, countY), countSize);
            }
        }
    }

    if (response.PlayerCount <= 0) return;

    for (int i = 0; i < response.PlayerCount; i++) {
        auto player = response.Players[i];
        if (player.Head == Vector3::Zero() || player.Toe == Vector3::Zero()) continue;
        if (!Actived.activar) continue;

        float distance = player.Distancia;
        if (distance < 0.1f || distance > 150.0f) continue;

        // --- POSITION FIX ---
        float headToFeetHeight = std::abs(player.Toe.Y - player.Head.Y);
        float boxHeight = headToFeetHeight * 1.15f; 
        float width = boxHeight * 0.48f;
        float boxY = player.Head.Y - (boxHeight * 0.12f);
        float boxX = player.Head.X - (width / 2.0f);

        Color currentBoxColor = pEspPlayer.espColor;
        if (player.IsBot) currentBoxColor = Color::Red();
        if (player.IsCaido) currentBoxColor = Color::Red(); // Changed from Yellow to Red

        float thickness = pEspPlayer.thickness;
        float fontSize = (distance > 70.0f) ? 10.0f : 12.0f;

        // --- RESTORED: LINE TYPES ---
        if (pEspPlayer.espLinha) {
            Vector3 lineStart;
            if (pEspPlayer.lineType == 0)      lineStart = Vector3(screenWidth / 2, 0, 0);
            else if (pEspPlayer.lineType == 1) lineStart = Vector3(screenWidth / 2, screenHeight / 2, 0);
            else if (pEspPlayer.lineType == 2) lineStart = Vector3(screenWidth / 2, screenHeight, 0);
            else                               lineStart = Vector3(screenWidth / 2, 0, 0);

            esp.DrawLine(currentBoxColor, thickness, lineStart, Vector3(player.Head.X, boxY, 0));
        }

        // --- RESTORED: BOX TYPES ---
        if (pEspPlayer.espCaixa) {
            Rect playerRect(boxX, boxY, width, boxHeight);

            if (pEspPlayer.boxType == 0) { // Stroke
                esp.DrawBox(currentBoxColor, thickness, playerRect);
            } 
            else if (pEspPlayer.boxType == 1) { // Filled
                Color fillColor = currentBoxColor;
                fillColor.a = 80;
                esp.DrawFilledRect(fillColor, Vector3(boxX, boxY, 0), width, boxHeight);
                esp.DrawBox(currentBoxColor, thickness, playerRect);
            } 
            else if (pEspPlayer.boxType == 2) { // Corner
                esp.DrawBox4Line(boxX, boxY, width, boxHeight, currentBoxColor, thickness);
            } 
            else if (pEspPlayer.boxType == 3) { // Rounded/Thick
                Color fillColor = currentBoxColor;
                fillColor.a = 60;
                esp.DrawFilledRect(fillColor, Vector3(boxX, boxY, 0), width, boxHeight);
                esp.DrawBox(currentBoxColor, thickness + 1.0f, playerRect);
            }

            // --- RESTORED: HEALTH BAR ---
            if (pEspPlayer.espHealth) {
                float healthBarWidth = (distance > 70.0f) ? 3.0f : 5.0f;
                float healthBarX = boxX - (healthBarWidth + 4);
                auto HealthMax = player.IsBot ? 100.0f : 200.0f;
                esp.DrawVerticalHealthBar(Vector2(healthBarX, boxY), boxHeight, HealthMax, player.Health, healthBarWidth, false);
            }
        }

        // --- UPDATED: PROFESSIONAL NAME + DISTANCE UI (with fixed vertical centering) ---
        if (pEspPlayer.espHealth) {
            std::string drawName;
            if (player.IsBot) drawName = "Bot";
            else if (player.PlayerName[0] != '\0') drawName = player.PlayerName;
            else drawName = std::to_string((int)(i + 1));

            std::string dStr = std::to_string((int)distance) + "M";

            float fontDrawSize = std::max(10.5f, fontSize - 1.0f);
            float spacing = 6.0f;
            float segmentPadding = 5.0f;
            float barPadding = 6.0f;
            float textHeight = fontDrawSize + 4.0f;  // height of each segment's background

            float nameTextWidth = drawName.length() * (fontDrawSize * 0.56f);
            float distTextWidth = dStr.length() * (fontDrawSize * 0.56f);
            float nameSegmentWidth = nameTextWidth + (segmentPadding * 2.0f);
            float distSegmentWidth = distTextWidth + (segmentPadding * 2.0f);
            float totalWidth = nameSegmentWidth + distSegmentWidth + spacing;

            float barHeight = textHeight + (barPadding * 2.0f);
            float barX = player.Head.X - (totalWidth * 0.5f) - barPadding;  // left edge of main panel
            float infoTopMargin = 24.0f;
            float barY = std::max(infoTopMargin, boxY - textHeight - 12.0f); // top edge of main panel

            auto DrawRoundedPanel = [&](float x, float y, float width, float height, float radius, Color color) {
                float clampedRadius = std::max(1.0f, std::min(radius, std::min(width, height) * 0.5f));
                esp.DrawFilledRect(color, Vector3(x + clampedRadius, y, 0), width - (clampedRadius * 2.0f), height);
                esp.DrawFilledRect(color, Vector3(x, y + clampedRadius, 0), clampedRadius, height - (clampedRadius * 2.0f));
                esp.DrawFilledRect(color, Vector3(x + width - clampedRadius, y + clampedRadius, 0), clampedRadius, height - (clampedRadius * 2.0f));
                esp.DrawFilledCircle(color, Vector2(x + clampedRadius, y + clampedRadius), clampedRadius);
                esp.DrawFilledCircle(color, Vector2(x + width - clampedRadius, y + clampedRadius), clampedRadius);
                esp.DrawFilledCircle(color, Vector2(x + clampedRadius, y + height - clampedRadius), clampedRadius);
                esp.DrawFilledCircle(color, Vector2(x + width - clampedRadius, y + height - clampedRadius), clampedRadius);
            };

            // Draw main panel background
            DrawRoundedPanel(barX, barY, totalWidth + (barPadding * 2.0f), barHeight, 4.0f, Color(0, 0, 0, 170));

            float currentX = player.Head.X - (totalWidth * 0.5f);  // left edge of first segment
            // Fix: Vertically center text inside each segment
            float textY = barY + barPadding + textHeight / 2.0f;   // vertical center of segment
            Color outlineColor = Color(0, 0, 0, 200);

            // Name segment
            DrawRoundedPanel(currentX, barY + barPadding, nameSegmentWidth, textHeight, 3.0f, Color(30, 30, 30, 210));
            float nameCenterX = currentX + (nameSegmentWidth * 0.5f);
            esp.DrawText(outlineColor, drawName.c_str(), Vector2(nameCenterX - 1, textY - 1), fontDrawSize);
            esp.DrawText(outlineColor, drawName.c_str(), Vector2(nameCenterX + 1, textY + 1), fontDrawSize);
            esp.DrawText(Color::White(), drawName.c_str(), Vector2(nameCenterX, textY), fontDrawSize);

            currentX += nameSegmentWidth + spacing;

            // Distance segment
            DrawRoundedPanel(currentX, barY + barPadding, distSegmentWidth, textHeight, 3.0f, Color(30, 30, 30, 210));
            float distCenterX = currentX + (distSegmentWidth * 0.5f);
            esp.DrawText(outlineColor, dStr.c_str(), Vector2(distCenterX - 1, textY - 1), fontDrawSize);
            esp.DrawText(outlineColor, dStr.c_str(), Vector2(distCenterX + 1, textY + 1), fontDrawSize);
            esp.DrawText(currentBoxColor, dStr.c_str(), Vector2(distCenterX, textY), fontDrawSize);
        }
    }
}