package de.lemaik.chunkymap.dynmap;

import de.lemaik.chunkymap.ChunkyMapPlugin;
import de.lemaik.chunkymap.rendering.Renderer;
import de.lemaik.chunkymap.rendering.local.ChunkyRenderer;
import de.lemaik.chunkymap.util.MinecraftDownloader;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import org.bukkit.Bukkit;
import org.dynmap.*;
import org.dynmap.hdmap.HDMap;
import org.dynmap.hdmap.IsoHDPerspective;
import org.dynmap.utils.TileFlags;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.json.JsonNumber;
import se.llbit.json.JsonObject;
import se.llbit.json.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * A map that uses the RenderService for rendering the tiles.
 */
public class ChunkyMap extends HDMap {
    private static final String DEFAULT_TEXTUREPACK_VERSION = "1.16.1";
    public final DynmapCameraAdapter cameraAdapter;
    private final Renderer renderer;
    private File defaultTexturepackPath;
    private File texturepackPath;
    private JsonObject templateScene;
    private int chunkPadding;

    public ChunkyMap(DynmapCore dynmap, ConfigurationNode config) {
        super(dynmap, config);
        cameraAdapter = new DynmapCameraAdapter((IsoHDPerspective) getPerspective());
        renderer = new ChunkyRenderer(
            config.getInteger("samplesPerPixel", 100),
            config.getBoolean("denoiser/enabled", false),
            config.getInteger("denoiser/albedoSamplesPerPixel", 16),
            config.getInteger("denoiser/normalSamplesPerPixel", 16),
            config.getInteger("chunkyThreads", 2)
        );
        chunkPadding = config.getInteger("chunkPadding", 0);

        String texturepackVersion = config.getString("texturepackVersion", DEFAULT_TEXTUREPACK_VERSION);
        ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class).getLogger()
            .info("Downloading additional textures for Minecraft " + texturepackVersion);
        try (Response response = MinecraftDownloader.downloadMinecraft(texturepackVersion).get()) {
          File tempFile = File.createTempFile("minecraft", ".jar");
          try (BufferedSink sink = Okio.buffer(Okio.sink(tempFile))) {
            sink.writeAll(response.body().source());
          }
          defaultTexturepackPath = tempFile;
        } catch (IOException | ExecutionException | InterruptedException e) {
          ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class).getLogger()
              .log(Level.SEVERE, "Downloading the textures failed, your Chunky dynmap might look bad!", e);
        }

        if (config.containsKey("texturepack")) {
            texturepackPath = Bukkit.getPluginManager().getPlugin("dynmap").getDataFolder().toPath()
                    .resolve(config.getString("texturepack"))
                    .toFile();
        } else {
            ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class).getLogger()
                    .warning("You didn't specify a texturepack for a map that is rendered with Chunky. " +
                            "The Minecraft " + texturepackVersion + " textures will be used.");
        }

        if (config.containsKey("templateScene")) {
            try (InputStream inputStream = new FileInputStream(Bukkit.getPluginManager().getPlugin("dynmap").getDataFolder().toPath()
                    .resolve(config.getString("templateScene"))
                    .toFile())) {
                templateScene = new JsonParser(inputStream).parse().asObject();
                templateScene.remove("world");
                templateScene.set("spp", new JsonNumber(0));
                templateScene.set("renderTime", new JsonNumber(0));
                templateScene.remove("chunkList");
                templateScene.remove("entities");
                templateScene.remove("actors");
            } catch (IOException | JsonParser.SyntaxError e) {
                ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class).getLogger()
                        .log(Level.SEVERE, "Could not read the template scene.", e);
            }
        }

        // texturepacks in chunky are static, so only load them once
        if (defaultTexturepackPath != null) {
            ChunkyRenderer.loadDefaultTexturepack(defaultTexturepackPath);
        }
        if (texturepackPath != null) {
            ChunkyRenderer.loadTexturepack(texturepackPath);
        }
    }

    @Override
    public void addMapTiles(List<MapTile> list, DynmapWorld world, int tx, int ty) {
        list.add(new ChunkyMapTile(world, getPerspective(), this, tx, ty));
    }

    public List<TileFlags.TileCoord> getTileCoords(DynmapWorld world, int x, int y, int z) {
        return getPerspective().getTileCoords(world, x, y, z);
    }

    public List<TileFlags.TileCoord> getTileCoords(DynmapWorld world, int minx, int miny, int minz, int maxx, int maxy, int maxz) {
        return getPerspective().getTileCoords(world, minx, miny, minz, maxx, maxy, maxz);
    }

    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        ChunkyMapTile t = (ChunkyMapTile) tile;
        DynmapWorld w = t.getDynmapWorld();
        int x = t.tileOrdinalX();
        int y = t.tileOrdinalY();

        return new MapTile[]{
                new ChunkyMapTile(w, getPerspective(), this, x - 1, y - 1),
                new ChunkyMapTile(w, getPerspective(), this, x - 1, y + 1),
                new ChunkyMapTile(w, getPerspective(), this, x + 1, y - 1),
                new ChunkyMapTile(w, getPerspective(), this, x + 1, y + 1),
                new ChunkyMapTile(w, getPerspective(), this, x, y - 1),
                new ChunkyMapTile(w, getPerspective(), this, x + 1, y),
                new ChunkyMapTile(w, getPerspective(), this, x, y + 1),
                new ChunkyMapTile(w, getPerspective(), this, x - 1, y)};
    }

    @Override
    public List<DynmapChunk> getRequiredChunks(MapTile mapTile) {
        return getPerspective().getRequiredChunks(mapTile);
    }

    @Override
    public List<MapType> getMapsSharingRender(DynmapWorld world) {
        ArrayList<MapType> maps = new ArrayList<>();
        for (MapType mt : world.maps) {
            if (mt instanceof ChunkyMap) {
                ChunkyMap chunkyMap = (ChunkyMap) mt;
                if (chunkyMap.getPerspective() == getPerspective() && chunkyMap.getBoostZoom() == getBoostZoom()) {
                    maps.add(mt);
                }
            }
        }
        return maps;
    }

    @Override
    public List<String> getMapNamesSharingRender(DynmapWorld dynmapWorld) {
        return getMapsSharingRender(dynmapWorld).stream().map(MapType::getName).collect(Collectors.toList());
    }

    Renderer getRenderer() {
        return renderer;
    }

    File getDefaultTexturepackPath(){
      return defaultTexturepackPath;
    }

    File getTexturepackPath() {
        return texturepackPath;
    }

    int getChunkPadding() {
        return chunkPadding;
    }

    void applyTemplateScene(Scene scene) {
        if (this.templateScene != null) {
            scene.importFromJson(templateScene);
        }
    }
}
