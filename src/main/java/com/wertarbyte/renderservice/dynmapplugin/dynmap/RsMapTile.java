package com.wertarbyte.renderservice.dynmapplugin.dynmap;

import com.wertarbyte.renderservice.dynmapplugin.rendering.ApiClient;
import com.wertarbyte.renderservice.dynmapplugin.rendering.RenderJob;
import org.bukkit.Bukkit;
import org.dynmap.*;
import org.dynmap.hdmap.HDMapTile;
import org.dynmap.hdmap.HDPerspective;
import org.dynmap.hdmap.IsoHDPerspective;
import org.dynmap.markers.impl.MarkerAPIImpl;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.utils.MapChunkCache;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.main.ChunkyOptions;
import se.llbit.chunky.renderer.RenderContext;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.chunky.world.World;
import se.llbit.util.ProgressListener;
import se.llbit.util.TaskTracker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class RsMapTile extends HDMapTile {
    private static final String RS3_API_URL = "https://api.rs.wertarbyte.com";
    private final RsMap map;

    public RsMapTile(DynmapWorld world, HDPerspective perspective, RsMap map, int tx, int ty) {
        super(world, perspective, tx, ty, 1);
        this.map = map;
    }

    public RsMapTile(DynmapWorld world, String parm) throws Exception {
        super(world, parm);
        map = (RsMap) world.maps.stream().filter(m -> m instanceof RsMap).findFirst().get();
    }

    /*
        public static void main(String[] args) {
            double basemodscale = 16;
            double inclination = 60;
            double azimuth = 135 + 90;
            int mapzoomout = 7;

            Matrix3D transform = new Matrix3D();
            transform.scale(1.0D / (double) basemodscale, 1.0D / (double) basemodscale, 1.0D / Math.sin(Math.toRadians(inclination)));
            transform.shearZ(0.0D, -Math.tan(Math.toRadians(90.0D - inclination)));
            transform.rotateYZ(-(90.0D - inclination));
            transform.rotateXY(-180.0D + azimuth);
            Matrix3D coordswap = new Matrix3D(0.0D, -1.0D, 0.0D, 0.0D, 0.0D, 1.0D, -1.0D, 0.0D, 0.0D);
            transform.multiply(coordswap);

            System.out.println(transform);

            double x = 0.5;
            double y = 7.5;
            Vector3D v = new Vector3D(x * (1 << mapzoomout), y * (1 << mapzoomout), 65);
            transform.transform(v);
            System.out.println("would be " + v);
        }
    */

    @Override
    public boolean render(MapChunkCache mapChunkCache, String s) {
        IsoHDPerspective perspective = (IsoHDPerspective) this.perspective;

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        MapStorage var52 = world.getMapStorage();
        MapStorageTile mtile = var52.getTile(world, map, tx, ty, 0, MapType.ImageVariant.STANDARD);

        int scaled = 0;
        if (boostzoom > 0 && MarkerAPIImpl.testTileForBoostMarkers(world, perspective, (double) (tx * 128), (double) (ty * 128), 128.0D)) {
            scaled = boostzoom;
        }

        int sizescale = 1 << scaled;

        try {
            World chunkyWorld = new World(Bukkit.getWorld(world.getRawName()).getWorldFolder(), true);
            Bukkit.getWorld(world.getRawName()).save();
            Scene scene = new Scene();
            scene.setName(tx + "_" + ty);
            scene.setCanvasSize(128 * sizescale, 128 * sizescale);
            map.cameraAdapter.apply(scene.camera(), tx, ty, world.getExtraZoomOutLevels() + map.getMapZoomOutLevels());

            scene.loadChunks(new TaskTracker(new ProgressListener() {
                @Override
                public void setProgress(String s, int i, int i1, int i2) {

                }

                @Override
                public void setProgress(String s, int i, int i1, int i2, String s1) {

                }
            }), chunkyWorld, perspective.getRequiredChunks(this).stream().map(c -> ChunkPosition.get(c.x, c.z)).collect(Collectors.toList()));
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
        } catch (Exception e) {
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
