package com.atw.optimalzone.render;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

public final class OverlayColorResolver {
    private static final float FALLBACK_RED = 0.68F;
    private static final float FALLBACK_GREEN = 0.68F;
    private static final float FALLBACK_BLUE = 0.68F;
    private static final float INVISIBLE_RED = 0.20F;
    private static final float INVISIBLE_GREEN = 0.85F;
    private static final float INVISIBLE_BLUE = 1.00F;
    private static final String CHAT_COLOR_CODES = "0123456789abcdef";
    private static final float COLOR_BRIGHTEN_AMOUNT = 0.35F;

    private OverlayColorResolver() {
    }

    public static Color colorFor(EntityPlayer player) {
        if (player == null) {
            return fallback();
        }

        Color teamColor = colorFromTeam(player, player.getTeam());
        if (teamColor != null) {
            return teamColor;
        }

        IChatComponent displayName = player.getDisplayName();
        if (displayName != null) {
            Color displayColor = colorFromFormattedText(displayName.getFormattedText());
            if (displayColor != null) {
                return displayColor;
            }
        }

        return fallback();
    }

    public static Color fallback() {
        return new Color(FALLBACK_RED, FALLBACK_GREEN, FALLBACK_BLUE);
    }

    public static Color invisiblePlayer() {
        return new Color(INVISIBLE_RED, INVISIBLE_GREEN, INVISIBLE_BLUE);
    }

    private static Color colorFromTeam(EntityPlayer player, Team team) {
        if (team == null) {
            return null;
        }

        Color formattedTeamNameColor = colorFromFormattedText(ScorePlayerTeam.formatPlayerName(team, player.getName()));
        if (formattedTeamNameColor != null) {
            return formattedTeamNameColor;
        }

        if (!(team instanceof ScorePlayerTeam)) {
            return null;
        }

        ScorePlayerTeam scorePlayerTeam = (ScorePlayerTeam) team;
        Color prefixColor = colorFromFormattedText(scorePlayerTeam.getColorPrefix());
        if (prefixColor != null) {
            return prefixColor;
        }

        Color suffixColor = colorFromFormattedText(scorePlayerTeam.getColorSuffix());
        if (suffixColor != null) {
            return suffixColor;
        }

        EnumChatFormatting chatFormat = scorePlayerTeam.getChatFormat();
        if (chatFormat == null || chatFormat.getColorIndex() < 0 || chatFormat.getColorIndex() > 15) {
            return null;
        }

        return colorFromIndex(chatFormat.getColorIndex());
    }

    private static Color colorFromFormattedText(String formattedText) {
        if (formattedText == null) {
            return null;
        }

        for (int index = 0; index < formattedText.length() - 1; index++) {
            if (formattedText.charAt(index) != '\u00A7') {
                continue;
            }

            int colorIndex = CHAT_COLOR_CODES.indexOf(Character.toLowerCase(formattedText.charAt(index + 1)));
            if (colorIndex >= 0) {
                return colorFromIndex(colorIndex);
            }
        }

        return null;
    }

    private static Color colorFromIndex(int colorIndex) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.fontRendererObj == null || colorIndex < 0 || colorIndex >= minecraft.fontRendererObj.colorCode.length) {
            return fallback();
        }

        int rgb = minecraft.fontRendererObj.colorCode[colorIndex];
        return fromRgb(rgb);
    }

    private static Color fromRgb(int rgb) {
        float red = ((rgb >> 16) & 255) / 255.0F;
        float green = ((rgb >> 8) & 255) / 255.0F;
        float blue = (rgb & 255) / 255.0F;

        return new Color(
                brighten(red),
                brighten(green),
                brighten(blue)
        );
    }

    private static float brighten(float component) {
        return component + (1.0F - component) * COLOR_BRIGHTEN_AMOUNT;
    }

    public static class Color {
        public final float red;
        public final float green;
        public final float blue;

        private Color(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public Color darker() {
            return darker(0.62F);
        }

        public Color darker(float amount) {
            return new Color(red * amount, green * amount, blue * amount);
        }
    }
}
