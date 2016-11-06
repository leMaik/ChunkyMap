package com.wertarbyte.renderservice.dynmapplugin.dynmap;

import com.wertarbyte.renderservice.dynmapplugin.rendering.ApiClient;
import com.wertarbyte.renderservice.dynmapplugin.rendering.RenderJob;
import org.bukkit.Bukkit;
import org.dynmap.*;
import org.dynmap.hdmap.HDMapTile;
import org.dynmap.hdmap.HDPerspective;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.utils.MapChunkCache;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.main.ChunkyOptions;
import se.llbit.chunky.renderer.RenderContext;
import se.llbit.chunky.renderer.projection.ProjectionMode;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.chunky.world.World;
import se.llbit.math.Vector3;
import se.llbit.util.ProgressListener;
import se.llbit.util.TaskTracker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class RsMapTile extends HDMapTile {
    private static final String RS3_API_URL = "https://api.rs.wertarbyte.com";
    private final RsMap map;
    private final int tx;
    private final int ty;

    public RsMapTile(DynmapWorld world, HDPerspective perspective, RsMap map, int tx, int ty) {
        super(world, perspective, tx, ty, 1);
        this.map = map;
        this.tx = tx;
        this.ty = ty;
    }

    public RsMapTile(DynmapWorld world, String parm) throws Exception {
        super(world, parm);

        String[] parms = parm.split(",");
        if (parms.length < 2) throw new Exception("wrong parameter count");
        this.tx = Integer.parseInt(parms[0]);
        this.ty = Integer.parseInt(parms[1]);
        map = (RsMap) world.maps.stream().filter(m -> m instanceof RsMap).findFirst().get();
    }

    @Override
    public boolean render(MapChunkCache mapChunkCache, String s) {
        System.out.println("Would now render tile " + tx + ";" + ty);

        double centerX = 24 + tx * 22.5 - ty * 26;
        double centerY = 1 - tx * 22.5 - ty * 26;
        System.out.println("Centered at " + centerX + " / " + centerY);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        MapStorage var52 = world.getMapStorage();
        MapStorageTile mtile = var52.getTile(world, map, tx, ty, 0, MapType.ImageVariant.STANDARD);

        try {
            // TODO render with RenderService
            // long crc = MapStorage.calculateImageHashCode(argb_buf[i], 0, argb_buf[i].length);
            // mtile.write()
            World chunkyWorld = new World(Bukkit.getWorld(world.getRawName()).getWorldFolder(), true);
            Bukkit.getWorld(world.getRawName()).save();
            Scene scene = new Scene();
            scene.setName(tx + "_" + ty);
            scene.setCanvasSize(256, 256);
            scene.camera().setProjectionMode(ProjectionMode.PARALLEL);
            scene.camera().setPosition(new Vector3(centerX, 61, centerY));
            scene.camera().setView(-0.7853981633974483, -0.5235987755982988, 0);
            scene.camera().setFoV(32.0);
            scene.camera().setDof(Double.POSITIVE_INFINITY);
            scene.loadChunks(new TaskTracker(new ProgressListener() {
                @Override
                public void setProgress(String s, int i, int i1, int i2) {

                }

                @Override
                public void setProgress(String s, int i, int i1, int i2, String s1) {

                }
            }), chunkyWorld, getChunksAround(centerX, centerY, 2));
            final File tempDir = Files.createTempDirectory("chunky-rs3").toFile();
            scene.saveScene(new RenderContext(new Chunky(ChunkyOptions.getDefaults())) {
                @Override
                public File getSceneDirectory() {
                    return tempDir;
                }

                @Override
                public File getSceneFile(String fileName) {
                    return new File(getSceneDirectory(), fileName);
                }
            }, new TaskTracker(new ProgressListener() {
                @Override
                public void setProgress(String s, int i, int i1, int i2) {

                }

                @Override
                public void setProgress(String s, int i, int i1, int i2, String s1) {

                }
            }));
            System.out.println("Scene saved to " + tempDir.getAbsolutePath());

            RenderJob job = new ApiClient(RS3_API_URL).createJob(
                    new File(tempDir, scene.name() + ".json"),
                    new File(tempDir, scene.name() + ".octree"),
                    new File(tempDir, scene.name() + ".grass"),
                    new File(tempDir, scene.name() + ".foliage"),
                    null,
                    new TaskTracker(
                            new ProgressListener() {
                                @Override
                                public void setProgress(String s, int i, int i1, int i2) {

                                }

                                @Override
                                public void setProgress(String s, int i, int i1, int i2, String s1) {

                                }
                            }
                    )
            ).get();
            // TODO remove temp dir

            new Thread(() -> {
                System.out.println("Waiting for renderer...");
                try {
                    while (new ApiClient(RS3_API_URL).getJob(job.getId()).get().getSpp() < 100) {
                        Thread.sleep(2000);
                    }
                    mtile.getWriteLock();
                    mtile.write((long) (Math.random() * 10000), new ApiClient(RS3_API_URL).getPicture(job.getId()));
                    MapManager.mapman.pushUpdate(getDynmapWorld(), new Client.Tile(mtile.getURI()));
                } catch (InterruptedException | ExecutionException | IOException e) {
                    return;
                } finally {
                    mtile.releaseWriteLock();
                }
            }).start();
        } catch (InterruptedException | IOException | ExecutionException e) {
            e.printStackTrace();
        }

        MapManager.mapman.updateStatistics(this, map.getPrefix(), true, true, false);
        return true;
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
