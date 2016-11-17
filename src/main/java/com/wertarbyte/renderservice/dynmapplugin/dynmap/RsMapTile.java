package com.wertarbyte.renderservice.dynmapplugin.dynmap;

import com.wertarbyte.renderservice.dynmapplugin.RsDynmapPlugin;
import com.wertarbyte.renderservice.dynmapplugin.rendering.FileBufferRenderContext;
import com.wertarbyte.renderservice.dynmapplugin.rendering.SilentTaskTracker;
import org.bukkit.Bukkit;
import org.dynmap.*;
import org.dynmap.hdmap.HDMapTile;
import org.dynmap.hdmap.HDPerspective;
import org.dynmap.hdmap.IsoHDPerspective;
import org.dynmap.markers.impl.MarkerAPIImpl;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.utils.MapChunkCache;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.chunky.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class RsMapTile extends HDMapTile {
    private final RsMap map;

    public RsMapTile(DynmapWorld world, HDPerspective perspective, RsMap map, int tx, int ty) {
        super(world, perspective, tx, ty, 1);
        this.map = map;
    }

    public RsMapTile(DynmapWorld world, String parm) throws Exception {
        super(world, parm);
        map = (RsMap) world.maps.stream().filter(m -> m instanceof RsMap).findFirst().get();
    }

    @Override
    public boolean render(MapChunkCache mapChunkCache, String s) {
        IsoHDPerspective perspective = (IsoHDPerspective) this.perspective;

        MapStorage var52 = world.getMapStorage();
        MapStorageTile mtile = var52.getTile(world, map, tx, ty, 0, MapType.ImageVariant.STANDARD);

        final int scaled = (boostzoom > 0 && MarkerAPIImpl.testTileForBoostMarkers(world, perspective, (double) (tx * 128), (double) (ty * 128), 128.0D)) ? boostzoom : 0;

        // TODO texturepack support
        FileBufferRenderContext context = new FileBufferRenderContext();
        try {
            map.getRenderer().render(context, (scene) -> {
                World chunkyWorld = new World(Bukkit.getWorld(world.getRawName()).getWorldFolder(), true);
                Bukkit.getWorld(world.getRawName()).save();
                scene.setName(tx + "_" + ty);
                scene.setCanvasSize(128 * (1 << scaled), 128 * (1 << scaled));
                map.cameraAdapter.apply(scene.camera(), tx, ty, world.getExtraZoomOutLevels() + map.getMapZoomOutLevels());

                // TODO add config options to increase chunk radius
                scene.loadChunks(SilentTaskTracker.INSTANCE, chunkyWorld,
                        perspective.getRequiredChunks(this).stream()
                                .map(c -> ChunkPosition.get(c.x, c.z))
                                .collect(Collectors.toList()));
            }).thenApply((image) -> {
                try {
                    mtile.getWriteLock();
                    mtile.write(image.hashCode(), image);
                    MapManager.mapman.pushUpdate(getDynmapWorld(), new Client.Tile(mtile.getURI()));
                } finally {
                    mtile.releaseWriteLock();
                    MapManager.mapman.updateStatistics(this, map.getPrefix(), true, true, false);
                }
                return true;
            }).get();
            return true;
        } catch (InterruptedException | ExecutionException e) {
            RsDynmapPlugin.getPlugin(RsDynmapPlugin.class).getLogger().log(Level.WARNING, "Rendering tile failed", e);
            return false;
        }
    }

    private Collection<ChunkPosition> getChunksAround(double centerX, double centerZ, int radius) {
        ArrayList<ChunkPosition> chunks = new ArrayList<>(4 * radius * radius + 4 * radius + 1);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                chunks.add(ChunkPosition.get((((int) centerX) >> 4) + x, (((int) centerZ) >> 4) + z));
            }
        }
        return chunks;
    }

    @Override
    public List<DynmapChunk> getRequiredChunks() {
        return map.getRequiredChunks(this);
    }

    @Override
    public MapTile[] getAdjecentTiles() {
        return map.getAdjecentTiles(this);
    }

    @Override
    public int hashCode() {
        return tx ^ ty ^ world.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof RsMapTile) {
            return ((RsMapTile) o).tx == tx && ((RsMapTile) o).ty == ty;
        }
        return false;
    }

    @Override
    public boolean isBiomeDataNeeded() {
        return false;
    }

    @Override
    public boolean isHightestBlockYDataNeeded() {
        return false;
    }

    @Override
    public boolean isRawBiomeDataNeeded() {
        return false;
    }

    @Override
    public boolean isBlockTypeDataNeeded() {
        return true;
    }

    @Override
    public int tileOrdinalX() {
        return tx;
    }

    @Override
    public int tileOrdinalY() {
        return ty;
    }

    @Override
    protected String saveTileData() {
        return String.format("%d,%d", tx, ty);
    }
}
