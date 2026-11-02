package com.atw.levelhead.data;

import java.util.UUID;

public class LevelTag {
    private static final String NICKED_FOOTER = "&c&lNICKED";
    private static final byte UNCLASSIFIED = 0;
    private static final byte NOT_NICKED = 1;
    private static final byte NICKED = 2;

    private final UUID owner;
    private final String header;
    private final String footer;
    private final String text;
    private final boolean nicked;
    private volatile byte nickedClassification;

    public LevelTag(UUID owner, String header, String footer) {
        this(owner, header, footer, false);
    }

    private LevelTag(UUID owner, String header, String footer, boolean nicked) {
        this.owner = owner;
        this.header = normalize(header);
        this.footer = normalize(footer);
        this.text = this.header + this.footer;
        this.nicked = nicked;
    }

    public static LevelTag nicked(UUID owner) {
        return new LevelTag(owner, "", NICKED_FOOTER, true);
    }

    public static LevelTag bedwarsNicked(UUID owner, String footer) {
        return new LevelTag(owner, "", footer, true);
    }

    public UUID getOwner() {
        return owner;
    }

    public String getHeader() {
        return header;
    }

    public String getFooter() {
        return footer;
    }

    public String getText() {
        return text;
    }

    public boolean isNicked() {
        if (nicked) {
            return true;
        }
        if (getClass() != LevelTag.class) {
            // A subclass can return changing text, null, or throw from getText().
            // Preserve the original virtual call and classification on every call.
            return "NICKED".equalsIgnoreCase(stripFormatting(getText()));
        }
        byte classification = nickedClassification;
        if (classification == UNCLASSIFIED) {
            classification = "NICKED".equalsIgnoreCase(stripFormatting(text)) ? NICKED : NOT_NICKED;
            // Volatile publishes the result to other readers. Racing first calls
            // may compute twice, but always derive the same result from final text.
            nickedClassification = classification;
        }
        return classification == NICKED;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('&', '\u00a7');
    }

    private static String stripFormatting(String value) {
        return value == null ? "" : value.replaceAll("\u00a7.", "");
    }
}
