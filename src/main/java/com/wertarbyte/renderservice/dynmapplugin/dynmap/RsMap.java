package com.wertarbyte.renderservice.dynmapplugin.dynmap;

import com.wertarbyte.renderservice.dynmapplugin.RsDynmapPlugin;
import com.wertarbyte.renderservice.dynmapplugin.rendering.Renderer;
import com.wertarbyte.renderservice.dynmapplugin.rendering.local.ChunkyRenderer;
import com.wertarbyte.renderservice.dynmapplugin.util.MinecraftDownloader;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import org.dynmap.*;
import org.dynmap.hdmap.HDMap;
import org.dynmap.hdmap.IsoHDPerspective;
import org.dynmap.utils.TileFlags;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * A map that uses the RenderService for rendering the tiles.
 */
public class RsMap extends HDMap {
    public final DynmapCameraAdapter cameraAdapter;
    private final Renderer renderer;
    private File texturepackPath;

    public RsMap(DynmapCore dynmap, ConfigurationNode config) {
        super(dynmap, config);
        cameraAdapter = new DynmapCameraAdapter((IsoHDPerspective) getPerspective());
        renderer = new ChunkyRenderer(
                config.getInteger("samplesPerPixel", 100),
                config.getInteger("chunkyThreads", 2)
        );

        if (!config.containsKey("texturepack")) {
            RsDynmapPlugin.getPlugin(RsDynmapPlugin.class).getLogger()
                    .warning("You didn't specify a texturepack for a map that is rendered with Chunky. " +
                            "The Minecraft 1.10 textures are now downloaded and will be used.");
            try (Response response = MinecraftDownloader.downloadMinecraft("1.10").get()) {
                texturepackPath = File.createTempFile("minecraft", ".jar");
                try (BufferedSink sink = Okio.buffer(Okio.sink(texturepackPath))) {
                    sink.writeAll(response.body().source());
                }
            } catch (IOException | ExecutionException | InterruptedException e) {
                texturepackPath = null;
                RsDynmapPlugin.getPlugin(RsDynmapPlugin.class).getLogger()
                        .log(Level.SEVERE, "Downloading the textures failed, your Chunky dynmap will look bad!", e);
            }
        }
    }

    @Override
    public void addMapTiles(List<MapTile> list, DynmapWorld world, int tx, int ty) {
        list.add(new RsMapTile(world, getPerspective(), this, tx, ty));
    }

    public List<TileFlags.TileCoord> getTileCoords(DynmapWorld world, int x, int y, int z) {
        return getPerspective().getTileCoords(world, x, y, z);
    }

    public List<TileFlags.TileCoord> getTileCoords(DynmapWorld world, int minx, int miny, int minz, int maxx, int maxy, int maxz) {
        return getPerspective().getTileCoords(world, minx, miny, minz, maxx, maxy, maxz);
    }

    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        RsMapTile t = (RsMapTile) tile;
        DynmapWorld w = t.getDynmapWorld();
        int x = t.tileOrdinalX();
        int y = t.tileOrdinalY();

        return new MapTile[]{
                new RsMapTile(w, getPerspective(), this, x - 1, y - 1),
                new RsMapTile(w, getPerspective(), this, x - 1, y + 1),
                new RsMapTile(w, getPerspective(), this, x + 1, y - 1),
                new RsMapTile(w, getPerspective(), this, x + 1, y + 1),
                new RsMapTile(w, getPerspective(), this, x, y - 1),
                new RsMapTile(w, getPerspective(), this, x + 1, y),
                new RsMapTile(w, getPerspective(), this, x, y + 1),
                new RsMapTile(w, getPerspective(), this, x - 1, y)};
    }

    @Override
    public List<DynmapChunk> getRequiredChunks(MapTile mapTile) {
        return getPerspective().getRequiredChunks(mapTile);
    }

    @Override
    public List<MapType> getMapsSharingRender(DynmapWorld world) {
        ArrayList<MapType> maps = new ArrayList<>();
        for (MapType mt : world.maps) {
            if (mt instanceof RsMap) {
                maps.add(mt);
            }
        }
        return maps;
    }

    @Override
    public List<String> getMapNamesSharingRender(DynmapWorld dynmapWorld) {
        return getMapsSharingRender(dynmapWorld).stream().map(MapType::getName).collect(Collectors.toList());
    }

    public Renderer getRenderer() {
        return renderer;
    }

    public File getTexturepackPath() {
        return texturepackPath;
    }
}
