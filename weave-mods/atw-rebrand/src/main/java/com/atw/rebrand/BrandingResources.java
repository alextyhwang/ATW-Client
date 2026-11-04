package com.atw.rebrand;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

/** A final, branding-only layer: player packs keep all their other textures. */
public final class BrandingResources implements IResourcePack {
    public static List<IResourcePack> append(List<IResourcePack> packs) {
        List<IResourcePack> result = new ArrayList<>(packs);
        result.removeIf(pack -> pack instanceof BrandingResources);
        result.add(new BrandingResources());
        return result;
    }

    private String path(ResourceLocation location) {
        if (!"lunar".equals(location.getResourceDomain())) return null;
        String path = location.getResourcePath();
        if (path.contains("..") || path.startsWith("/")) return null;
        return "/atw-branding/assets/lunar/" + path;
    }

    @Override public InputStream getInputStream(ResourceLocation location) throws IOException {
        String path = path(location);
        InputStream stream = path == null ? null : BrandingResources.class.getResourceAsStream(path);
        if (stream == null) throw new FileNotFoundException(location.toString());
        return stream;
    }
    @Override public boolean resourceExists(ResourceLocation location) {
        String path = path(location);
        return path != null && BrandingResources.class.getResource(path) != null;
    }
    @Override public Set<String> getResourceDomains() { return Collections.singleton("lunar"); }
    @Override public <T extends IMetadataSection> T getPackMetadata(IMetadataSerializer serializer, String section) { return null; }
    @Override public BufferedImage getPackImage() throws IOException {
        try (InputStream stream = BrandingResources.class.getResourceAsStream("/atw-rebrand/logo.png")) {
            return javax.imageio.ImageIO.read(stream);
        }
    }
    @Override public String getPackName() { return "ATW Client branding"; }
}
