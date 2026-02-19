package de.lemaik.chunkymap;

import org.bukkit.Bukkit;

import java.io.File;

public class SpigotPlatform extends Platform {
    private ChunkyMapPlugin plugin;

    public SpigotPlatform(ChunkyMapPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public File getDataFolder() {
        return plugin.getDataFolder();
    }

    @Override
    public File getDynmapDataFolder() {
        return Bukkit.getPluginManager().getPlugin("dynmap").getDataFolder();
    }

    @Override
    public String getMinecraftVersion() {
        return Bukkit.getVersion().split("-")[0];
    }
}
