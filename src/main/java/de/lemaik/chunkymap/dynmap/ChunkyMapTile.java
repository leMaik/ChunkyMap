package de.lemaik.chunkymap.dynmap;

import de.lemaik.chunkymap.ChunkyMapPlugin;
import de.lemaik.chunkymap.rendering.FileBufferRenderContext;
import de.lemaik.chunkymap.rendering.Renderer;
import de.lemaik.chunkymap.rendering.SilentTaskTracker;
import de.lemaik.chunkymap.rendering.rs.RemoteRenderer;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.World.Environment;
import org.dynmap.Client.Tile;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapWorld;
import org.dynmap.MapManager;
import org.dynmap.MapTile;
import org.dynmap.MapType.ImageVariant;
import org.dynmap.MapTypeState;
import org.dynmap.hdmap.HDMapTile;
import org.dynmap.hdmap.HDPerspective;
import org.dynmap.hdmap.IsoHDPerspective;
import org.dynmap.markers.impl.MarkerAPIImpl;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.utils.MapChunkCache;
import se.llbit.chunky.entity.PlayerEntity;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.chunky.world.World;
import se.llbit.chunky.world.World.LoggedWarnings;
import se.llbit.util.ProgressListener;
import se.llbit.util.TaskTracker;

public class ChunkyMapTile extends HDMapTile {

  private final ChunkyMap map;

  public ChunkyMapTile(DynmapWorld world, HDPerspective perspective, ChunkyMap map, int tx,
      int ty) {
    super(world, perspective, tx, ty, map.getBoostZoom());
    this.map = map;
  }

  public ChunkyMapTile(DynmapWorld world, String parm) throws Exception {
    // Do not remove this constructor! It is used by Dynmap to de-serialize tiles from the queue.
    // The serialization happens in the inherited saveTileData() method.
    super(world, parm);
    map = (ChunkyMap) world.maps.stream().filter(m -> m instanceof ChunkyMap).findFirst().get();
  }

  @Override
  public boolean render(MapChunkCache mapChunkCache, String s) {
    IsoHDPerspective perspective = (IsoHDPerspective) this.perspective;

    final int scaled = (boostzoom > 0 && MarkerAPIImpl
        .testTileForBoostMarkers(world, perspective, (double) (tx * 128), (double) (ty * 128),
            128.0D)) ? boostzoom : 0;

    // Mark the tiles we're going to render as validated
    MapTypeState mts = world.getMapState(map);
    if (mts != null) {
      mts.validateTile(tx, ty);
    }

    FileBufferRenderContext context = new FileBufferRenderContext();
    try {
      Renderer renderer = map.getRenderer();
      renderer.setDefaultTexturepack(map.getDefaultTexturepackPath());
      renderer.render(context, map.getTexturepackPath(), (scene) -> {
        org.bukkit.World bukkitWorld = Bukkit.getWorld(world.getRawName());
        World chunkyWorld = World.loadWorld(bukkitWorld.getWorldFolder(),
            getChunkyDimension(bukkitWorld.getEnvironment()), LoggedWarnings.SILENT);
        // Bukkit.getScheduler().runTask(ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class), Bukkit.getWorld(world.getRawName())::save);
        map.applyTemplateScene(scene);
        scene.setName(tx + "_" + ty);
        scene.setCanvasSize(128 * (1 << scaled), 128 * (1 << scaled));
        scene.setTransparentSky(true);
        scene.setYClipMin((int) perspective.minheight);
        if (perspective.maxheight > 0) {
          scene.setYClipMax((int) perspective.maxheight);
        }
        map.cameraAdapter.apply(scene.camera(), tx, ty, map.getMapZoomOutLevels(),
            world.getExtraZoomOutLevels());

        if (renderer instanceof RemoteRenderer && ((RemoteRenderer) renderer)
            .shouldInitializeLocally()) {
          scene.loadChunks(SilentTaskTracker.INSTANCE, chunkyWorld,
              perspective.getRequiredChunks(this).stream()
                  .flatMap(c -> getChunksAround(c.x, c.z, map.getChunkPadding()).stream())
                  .collect(Collectors.toList()));
          scene.getActors().removeIf(actor -> actor instanceof PlayerEntity);
          try {
            scene.saveScene(context, new TaskTracker(ProgressListener.NONE));
          } catch (IOException e) {
            throw new RuntimeException("Could not save scene", e);
          }
        } else {
          try {
            Field chunks = Scene.class.getDeclaredField("chunks");
            chunks.setAccessible(true);
            Collection<ChunkPosition> chunksList = (Collection<ChunkPosition>) chunks.get(scene);
            chunksList.clear();
            perspective.getRequiredChunks(this).stream()
                .flatMap(c -> getChunksAround(c.x, c.z, map.getChunkPadding()).stream())
                .forEach(chunksList::add);
          } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not set chunks", e);
          }
          try {
            Field worldPath = Scene.class.getDeclaredField("worldPath");
            worldPath.setAccessible(true);
            worldPath.set(scene, bukkitWorld.getWorldFolder().getAbsolutePath());
            Field worldDimension = Scene.class.getDeclaredField("worldDimension");
            worldDimension.setAccessible(true);
            worldDimension.setInt(scene, bukkitWorld.getEnvironment().getId());
          } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not set world", e);
          }
          try {
            scene.saveDescription(context.getSceneDescriptionOutputStream(scene.name));
          } catch (IOException e) {
            throw new RuntimeException("Could not save scene", e);
          }
        }
      }).thenApply((image) -> {
        MapStorage var52 = world.getMapStorage();
        MapStorageTile mtile = var52.getTile(world, map, tx, ty, 0, ImageVariant.STANDARD);
        MapManager mapManager = MapManager.mapman;
        if (mapManager != null) {
          mtile.getWriteLock();
          try {
            mtile.write(image.hashCode(), image);
            mapManager.pushUpdate(getDynmapWorld(), new Tile(mtile.getURI()));
          } finally {
            mtile.releaseWriteLock();
          }
          mapManager.updateStatistics(this, map.getPrefix(), true, true, false);
        }
        return true;
      }).get();
      return true;
    } catch (Exception e) {
      ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class).getLogger()
          .log(Level.WARNING, "Rendering tile failed", e);
      return false;
    } finally {
      context.dispose();
    }
  }

  private static int getChunkyDimension(Environment environment) {
    switch (environment) {
      case NETHER:
        return World.NETHER_DIMENSION;
      case THE_END:
        return World.END_DIMENSION;
      case NORMAL:
      default:
        return World.OVERWORLD_DIMENSION;
    }
  }

  private static Collection<ChunkPosition> getChunksAround(int centerX, int centerZ, int radius) {
    ArrayList<ChunkPosition> chunks = new ArrayList<>((radius + 1) * (radius + 1));
    for (int x = -radius; x <= radius; x++) {
      for (int z = -radius; z <= radius; z++) {
        chunks.add(ChunkPosition.get(centerX + x, centerZ + z));
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

  public int hashCode() {
    return this.tx ^ this.ty ^ this.perspective.hashCode() ^ this.world.hashCode() ^ this.boostzoom;
  }

  public boolean equals(Object obj) {
    return obj instanceof ChunkyMapTile && this.equals((ChunkyMapTile) obj);
  }

  public boolean equals(ChunkyMapTile o) {
    return o.tx == this.tx && o.ty == this.ty && this.perspective == o.perspective
        && o.world == this.world && o.boostzoom == this.boostzoom;
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
}
