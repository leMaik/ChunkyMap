package de.lemaik.chunkymap;

import java.io.File;

public abstract class Platform {
    private static Platform instance;

    public static Platform getInstance() {
        return instance;
    }

    public static void setInstance(Platform instance) {
        Platform.instance = instance;
    }

    public abstract File getDataFolder();
    public abstract File getDynmapDataFolder();
    public abstract String getMinecraftVersion();
}
