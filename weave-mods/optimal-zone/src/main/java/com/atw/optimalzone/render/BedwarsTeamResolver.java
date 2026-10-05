package com.atw.optimalzone.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

import java.util.Collection;
import java.util.Locale;

final class BedwarsTeamResolver {
    static final int BED_UNKNOWN = 0;
    static final int BED_ALIVE = 1;
    static final int BED_DESTROYED = -1;

    private BedwarsTeamResolver() {
    }

    static boolean isGameInProgress(Minecraft mc) {
        if (!isHypixel(mc) || mc.theWorld == null) {
            return false;
        }

        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) {
            return false;
        }

        boolean bedwars = containsBedwars(clean(objective.getDisplayName()));
        boolean teamStatusMarker = false;
        Collection<Score> scores = scoreboard.getSortedScores(objective);
        for (Score score : scores) {
            if (score == null || score.getPlayerName() == null || score.getPlayerName().startsWith("#")) {
                continue;
            }

            ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            String line = clean(ScorePlayerTeam.formatPlayerName(team, score.getPlayerName()));
            bedwars |= containsBedwars(line);
            teamStatusMarker |= isTeamStatusMarker(line);
        }
        return bedwars && teamStatusMarker;
    }

    static int teamKey(Minecraft mc, EntityPlayer player) {
        return resolveTeamKey(playerInfo(mc, player), player);
    }

    static String teamName(int teamKey) {
        switch (teamKey) {
            case 1:
                return "Red";
            case 2:
                return "Blue";
            case 3:
                return "Green";
            case 4:
                return "Yellow";
            case 5:
                return "Aqua";
            case 6:
                return "White";
            case 7:
                return "Pink";
            case 8:
                return "Gray";
            default:
                return "Unknown";
        }
    }

    static int bedStatus(Minecraft mc, int teamKey) {
        if (mc == null || mc.theWorld == null) {
            return BED_UNKNOWN;
        }

        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) {
            return BED_UNKNOWN;
        }

        String teamMarker = teamKey == 0
                ? null
                : teamName(teamKey).toUpperCase(Locale.ROOT) + ":";
        Collection<Score> scores = scoreboard.getSortedScores(objective);
        for (Score score : scores) {
            if (score == null || score.getPlayerName() == null || score.getPlayerName().startsWith("#")) {
                continue;
            }

            ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            String line = clean(ScorePlayerTeam.formatPlayerName(team, score.getPlayerName()));
            if (!isTeamStatusMarker(line)) {
                continue;
            }

            int status = bedStatusFromLine(line);
            if (status == BED_UNKNOWN) {
                continue;
            }

            if (line.endsWith(" YOU") || line.contains(" YOU ")) {
                return status;
            }
            if (teamMarker != null && line.contains(teamMarker)) {
                return status;
            }
        }
        return BED_UNKNOWN;
    }

    static boolean isHypixel(Minecraft mc) {
        if (mc == null) {
            return false;
        }

        ServerData data = mc.getCurrentServerData();
        if (data == null || data.serverIP == null) {
            return false;
        }

        String normalized = data.serverIP.toLowerCase(Locale.ROOT);
        return normalized.contains("hypixel.net") || normalized.contains("hypixel.io");
    }

    private static NetworkPlayerInfo playerInfo(Minecraft mc, EntityPlayer player) {
        if (mc == null || mc.getNetHandler() == null || player == null) {
            return null;
        }

        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
        return info == null ? mc.getNetHandler().getPlayerInfo(player.getName()) : info;
    }

    private static int resolveTeamKey(NetworkPlayerInfo info, EntityPlayer player) {
        String playerName = player == null ? null : player.getName();
        ScorePlayerTeam team = null;
        String formattedName = null;
        if (info != null) {
            playerName = info.getGameProfile().getName();
            team = info.getPlayerTeam();
            formattedName = info.getDisplayName() == null
                    ? ScorePlayerTeam.formatPlayerName(team, playerName)
                    : info.getDisplayName().getFormattedText();
        } else if (player != null) {
            if (player.getTeam() instanceof ScorePlayerTeam) {
                team = (ScorePlayerTeam) player.getTeam();
            }
            formattedName = player.getDisplayName() == null
                    ? ScorePlayerTeam.formatPlayerName(team, playerName)
                    : player.getDisplayName().getFormattedText();
        }

        EnumChatFormatting color = colorAtPlayerName(formattedName, playerName);
        if (colorTeamKey(color) == 0 && team != null) {
            color = team.getChatFormat();
        }
        return colorTeamKey(color);
    }

    private static EnumChatFormatting colorAtPlayerName(String formattedText, String playerName) {
        if (formattedText == null || playerName == null || playerName.isEmpty()) {
            return null;
        }

        StringBuilder visible = new StringBuilder();
        StringBuilder colorCodes = new StringBuilder();
        char activeColor = 0;
        for (int index = 0; index < formattedText.length(); index++) {
            char current = formattedText.charAt(index);
            if (current == '\u00A7' && index + 1 < formattedText.length()) {
                char code = Character.toLowerCase(formattedText.charAt(++index));
                if ("0123456789abcdef".indexOf(code) >= 0) {
                    activeColor = code;
                } else if (code == 'r') {
                    activeColor = 0;
                }
                continue;
            }

            visible.append(current);
            colorCodes.append(activeColor);
        }

        int nameIndex = visible.toString().toLowerCase(Locale.ROOT)
                .lastIndexOf(playerName.toLowerCase(Locale.ROOT));
        if (nameIndex < 0 || nameIndex >= colorCodes.length()) {
            return null;
        }
        return colorByCode(colorCodes.charAt(nameIndex));
    }

    private static EnumChatFormatting colorByCode(char code) {
        for (EnumChatFormatting formatting : EnumChatFormatting.values()) {
            if (formatting.isColor() && formatting.toString().length() >= 2
                    && Character.toLowerCase(formatting.toString().charAt(1)) == code) {
                return formatting;
            }
        }
        return null;
    }

    private static int colorTeamKey(EnumChatFormatting color) {
        if (color == EnumChatFormatting.RED || color == EnumChatFormatting.DARK_RED) {
            return 1;
        }
        if (color == EnumChatFormatting.BLUE || color == EnumChatFormatting.DARK_BLUE) {
            return 2;
        }
        if (color == EnumChatFormatting.GREEN || color == EnumChatFormatting.DARK_GREEN) {
            return 3;
        }
        if (color == EnumChatFormatting.YELLOW || color == EnumChatFormatting.GOLD) {
            return 4;
        }
        if (color == EnumChatFormatting.AQUA || color == EnumChatFormatting.DARK_AQUA) {
            return 5;
        }
        if (color == EnumChatFormatting.WHITE) {
            return 6;
        }
        if (color == EnumChatFormatting.LIGHT_PURPLE || color == EnumChatFormatting.DARK_PURPLE) {
            return 7;
        }
        if (color == EnumChatFormatting.GRAY || color == EnumChatFormatting.DARK_GRAY) {
            return 8;
        }
        return 0;
    }

    private static boolean containsBedwars(String text) {
        return text.contains("BED WARS") || text.contains("BEDWARS");
    }

    private static boolean isTeamStatusMarker(String line) {
        return line.contains("RED:")
                || line.contains("BLUE:")
                || line.contains("GREEN:")
                || line.contains("YELLOW:")
                || line.contains("AQUA:")
                || line.contains("WHITE:")
                || line.contains("PINK:")
                || line.contains("GRAY:");
    }

    private static int bedStatusFromLine(String line) {
        if (line.contains("\u2714") || line.contains("\u2713")) {
            return BED_ALIVE;
        }
        if (line.contains("\u2718") || line.contains("\u2716") || line.contains("\u2717")
                || line.contains("\u2715")) {
            return BED_DESTROYED;
        }

        int markerIndex = line.indexOf(':');
        if (markerIndex >= 0 && line.substring(markerIndex + 1).trim().startsWith("X")) {
            return BED_DESTROYED;
        }
        return BED_UNKNOWN;
    }

    private static String clean(String value) {
        String clean = EnumChatFormatting.getTextWithoutFormattingCodes(value);
        return clean == null ? "" : clean.trim().toUpperCase(Locale.ROOT);
    }
}
