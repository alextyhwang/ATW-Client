package com.atw.renderboost.cache;

/** Exact primitive inputs; signed zero is deliberately distinct. */
public final class GlyphKey {
    public final Object font;
    public final int u, v, shear, width, x, y;
    private final int hash;

    public GlyphKey(Object font, int u, int v, int shear, float width, float x, float y) {
        this.font = font;
        this.u = u;
        this.v = v;
        this.shear = shear;
        this.width = Float.floatToIntBits(width);
        this.x = Float.floatToIntBits(x);
        this.y = Float.floatToIntBits(y);
        int h = System.identityHashCode(font);
        h = 31 * h + u;
        h = 31 * h + v;
        h = 31 * h + shear;
        h = 31 * h + this.width;
        h = 31 * h + this.x;
        hash = 31 * h + this.y;
    }

    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object obj) {
        if (!(obj instanceof GlyphKey)) return false;
        GlyphKey b = (GlyphKey) obj;
        return font == b.font && u == b.u && v == b.v && shear == b.shear
                && width == b.width && x == b.x && y == b.y;
    }
}
