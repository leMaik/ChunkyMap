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
import se.llbit.chunky.renderer.RenderManager;
import se.llbit.chunky.renderer.Renderer;
import se.llbit.chunky.renderer.SnapshotControl;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.renderer.scene.SynchronousSceneManager;
import se.llbit.chunky.resources.BitmapImage;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.chunky.world.World;
import se.llbit.util.TaskTracker;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
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

        MapStorage var52 = world.getMapStorage();
        MapStorageTile mtile = var52.getTile(world, map, tx, ty, 0, MapType.ImageVariant.STANDARD);

        int scaled = 0;
        if (boostzoom > 0 && MarkerAPIImpl.testTileForBoostMarkers(world, perspective, (double) (tx * 128), (double) (ty * 128), 128.0D)) {
            scaled = boostzoom;
        }

        // TODO re-use context, renderer and sceneManager
        // TODO texturepack support
        try {
            FileBufferRenderContext context = new FileBufferRenderContext();
            Renderer renderer = new RenderManager(context, true);
            SynchronousSceneManager sceneManager = new SynchronousSceneManager(context, renderer);

            World chunkyWorld = new World(Bukkit.getWorld(world.getRawName()).getWorldFolder(), true);
            Bukkit.getWorld(world.getRawName()).save();
            Scene scene = sceneManager.getScene();
            scene.setName(tx + "_" + ty);
            scene.setCanvasSize(128 * (1 << scaled), 128 * (1 << scaled));
            map.cameraAdapter.apply(scene.camera(), tx, ty, world.getExtraZoomOutLevels() + map.getMapZoomOutLevels());

            scene.loadChunks(SilentTaskTracker.INSTANCE, chunkyWorld,
                    perspective.getRequiredChunks(this).stream()
                            .map(c -> ChunkPosition.get(c.x, c.z))
                            .collect(Collectors.toList()));

            renderer.setSceneProvider(sceneManager);
            renderer.setSnapshotControl(new SnapshotControl() {
                @Override
                public boolean saveSnapshot(Scene scene, int nextSpp) {
                    return false;
                }

                @Override
                public boolean saveRenderDump(Scene scene, int nextSpp) {
                    return false;
                }
            });
            renderer.setNumThreads(2);
            renderer.setOnRenderCompleted((time, sps) -> {
                try {
                    mtile.getWriteLock();

                    Class<Scene> sceneClass = Scene.class;
                    Method computeAlpha = sceneClass.getDeclaredMethod("computeAlpha", TaskTracker.class);
                    computeAlpha.setAccessible(true);
                    computeAlpha.invoke(scene, SilentTaskTracker.INSTANCE);

                    Field finalized = sceneClass.getDeclaredField("finalized");
                    finalized.setAccessible(true);
                    if (!finalized.getBoolean(scene)) {
                        scene.postProcessFrame(SilentTaskTracker.INSTANCE);
                    }

                    Field backBuffer = sceneClass.getDeclaredField("backBuffer");
                    backBuffer.setAccessible(true);
                    BitmapImage img = (BitmapImage) backBuffer.get(scene);
                    BufferedImage bufferedImage = new BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < img.height; y++) {
                        for (int x = 0; x < img.width; x++) {
                            bufferedImage.setRGB(x, y, img.getPixel(x, y));
                        }
                    }
                    mtile.write(bufferedImage.hashCode(), bufferedImage);
                    MapManager.mapman.pushUpdate(getDynmapWorld(), new Client.Tile(mtile.getURI()));
                } catch (Exception e) {
                    RsDynmapPlugin.getPlugin(RsDynmapPlugin.class).getLogger().log(Level.SEVERE, "Could not save a rendered tile", e);
                } finally {
                    mtile.releaseWriteLock();
                    MapManager.mapman.updateStatistics(this, map.getPrefix(), true, true, false);
                }
            });

            try {
                sceneManager.getScene().startHeadlessRender();
                sceneManager.getScene().setTargetSpp(100);
                renderer.start();
                renderer.join();
            } finally {
                renderer.shutdown();
            }
        } catch (Exception e) {
            RsDynmapPlugin.getPlugin(RsDynmapPlugin.class).getLogger().log(Level.SEVERE, "Could not render a tile", e);
            return false;
        }

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
