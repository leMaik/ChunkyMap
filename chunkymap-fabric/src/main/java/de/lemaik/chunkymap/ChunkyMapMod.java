package de.lemaik.chunkymap;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.llbit.log.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ChunkyMapMod implements ModInitializer {
    public static final String MOD_ID = "chunkymap";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Log.setReceiver(new ChunkyLogAdapter(LOGGER), se.llbit.log.Level.ERROR,
                se.llbit.log.Level.WARNING, se.llbit.log.Level.INFO);

        Platform.setInstance(new FabricPlatform());

        try (InputStream mapIcon = getClass().getClassLoader().getResourceAsStream("rs-map-icon.png")) {
            Path iconPath = Paths
                    .get(Platform.getInstance().getDynmapDataFolder().getAbsolutePath(), "web", "images", "block_chunky.png");
            if (!iconPath.toFile().exists()) {
                Files.copy(mapIcon, iconPath);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not write chunky map icon", e);
        }
    }
}
