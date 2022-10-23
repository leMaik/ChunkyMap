package de.lemaik.chunkymap.dynmap;

import de.lemaik.chunkymap.ChunkyMapPlugin;
import de.lemaik.chunkymap.rendering.FileBufferRenderContext;
import de.lemaik.chunkymap.rendering.Renderer;
import de.lemaik.chunkymap.rendering.SilentTaskTracker;
import de.lemaik.chunkymap.rendering.rs.RemoteRenderer;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.World.Environment;
import org.dynmap.*;
import org.dynmap.Client.Tile;
import org.dynmap.MapType.ImageVariant;
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
  public ChunkyMapTile(DynmapWorld world, HDPerspective perspective, int tx, int ty, int boostzoom, int tilescale) {
    super(world, perspective, tx, ty, boostzoom, tilescale);
  }

  public ChunkyMapTile(DynmapWorld world, String parm) throws Exception {
    // Do not remove this constructor! It is used by Dynmap to de-serialize tiles from the queue.
    // The serialization happens in the inherited saveTileData() method.
    super(world, parm);
  }

  @Override
  public boolean render(MapChunkCache mapChunkCache, String mapName) {
    final long startTimestamp = System.currentTimeMillis();
    IsoHDPerspective perspective = (IsoHDPerspective) this.perspective;

    final int scaled = (boostzoom > 0 && MarkerAPIImpl
        .testTileForBoostMarkers(world, perspective, (double) (tx * 128), (double) (ty * 128),
            128.0D)) ? boostzoom : 0;

    // Mark the tiles we're going to render as validated
    ChunkyMap map = (ChunkyMap) world.maps.stream()
        .filter(m -> m instanceof ChunkyMap && (mapName == null || m.getName().equals(mapName))
            && ((ChunkyMap) m).getPerspective() == perspective
            && ((ChunkyMap) m).getBoostZoom() == boostzoom)
        .findFirst().get();
    MapTypeState mts = world.getMapState(map);
    if (mts != null) {
      mts.validateTile(tx, ty);
    }

    FileBufferRenderContext context = new FileBufferRenderContext();
    try {
      Renderer renderer = map.getRenderer();
      renderer.setDefaultTexturepack(map.getDefaultTexturepackPath());
      renderer.render(context, map.getResourcepackPaths(), (scene) -> {
        org.bukkit.World bukkitWorld = Bukkit.getWorld(world.getRawName());
        World chunkyWorld = World.loadWorld(map.getWorldFolder(world),
            getChunkyDimension(bukkitWorld.getEnvironment()), LoggedWarnings.SILENT);
        // Bukkit.getScheduler().runTask(ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class), Bukkit.getWorld(world.getRawName())::save);
        map.applyTemplateScene(scene);
        scene.setName(tx + "_" + ty);
        scene.setCanvasSize(128 * (1 << scaled), 128 * (1 << scaled));
        scene.setTransparentSky(true);
        scene.setYClipMin((int) perspective.minheight);
        if (perspective.minheight == -2.147483648E9D) {
          scene.setYClipMin(world.minY);
        }
        scene.setYClipMax((int) perspective.maxheight);
        if (perspective.maxheight == -2.147483648E9D) {
          if (world.isNether()) {
            scene.setYClipMax(127);
          } else {
            scene.setYClipMax(world.worldheight - 1);
          }
        }
        map.cameraAdapter.apply(scene.camera(), tx, ty, map.getMapZoomOutLevels(),
            world.getExtraZoomOutLevels());

        if (renderer instanceof RemoteRenderer) {
          if (((RemoteRenderer) renderer).shouldInitializeLocally()) {
              Set<ChunkPosition> chunks = perspective.getRequiredChunks(this).stream()
                      .flatMap(c -> getChunksAround(c.x, c.z, map.getChunkPadding()).stream())
                      .collect(Collectors.toSet());
              Bukkit.getLogger().info("loading " + chunks.size()+ " chunks");
              scene.setOctreeImplementation("PACKED");
            scene.loadChunks(new TaskTracker((task, done, start, target) -> {
              Bukkit.getLogger().info(task + " ("+done+"/"+target+")");
            }), chunkyWorld, chunks);
            Bukkit.getLogger().info("loaded " + chunks.size()+ " chunks");
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
              chunksList.addAll(perspective.getRequiredChunks(this).stream()
                  .flatMap(c -> getChunksAround(c.x, c.z, map.getChunkPadding()).stream())
                  .collect(Collectors.toSet()));
            } catch (ReflectiveOperationException e) {
              throw new RuntimeException("Could not set chunks", e);
            }
            try {
              Field worldPath = Scene.class.getDeclaredField("worldPath");
              worldPath.setAccessible(true);
              worldPath.set(scene, map.getWorldFolder(world).getAbsolutePath());
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
        } else {
          Set<ChunkPosition> chunks = perspective.getRequiredChunks(this).stream()
                  .flatMap(c -> getChunksAround(c.x, c.z, map.getChunkPadding()).stream())
                  .collect(Collectors.toSet());
          scene.loadChunks(SilentTaskTracker.INSTANCE, chunkyWorld, chunks);
        }
      }).thenApply((image) -> {
        MapStorage var52 = world.getMapStorage();
        MapStorageTile mtile = var52.getTile(world, map, tx, ty, 0, ImageVariant.STANDARD);
        MapManager mapManager = MapManager.mapman;
        boolean tileUpdated = false;
        if (mapManager != null) {
          DataBufferInt dataBuffer = (DataBufferInt) image.getRaster().getDataBuffer();
          int[] data = dataBuffer.getData();
          long crc = MapStorage.calculateImageHashCode(data, 0, data.length);
          mtile.getWriteLock();
          try {
            if (!mtile.matchesHashCode(crc)) {
              mtile.write(crc, image, startTimestamp);
              mapManager.pushUpdate(getDynmapWorld(), new Tile(mtile.getURI()));
              tileUpdated = true;
            }
          } finally {
            mtile.releaseWriteLock();
          }
          mapManager.updateStatistics(this, map.getPrefix(), true, true, false);
        }
        return tileUpdated;
      }).get();
      return true;
    } catch (Exception e) {
      ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class).getLogger()
          .log(Level.WARNING, "Rendering tile " + tx + "_" + ty + " failed", e);

      if (map.getRequeueFailedTiles()) {
        // Re-queue the failed tile
        // Somewhat hacky but works surprisingly well
        MapManager.mapman.tileQueue.push(this);
      }
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
  public MapTile[] getAdjecentTiles() {
    return ChunkyMap.getAdjecentTilesOfTile(this, perspective);
  }

  @Override
  public boolean equals(HDMapTile o) {
    return o instanceof ChunkyMapTile && o.tx == this.tx && o.ty == this.ty && this.perspective == o.perspective && ((ChunkyMapTile) o).world == this.world && o.boostzoom == this.boostzoom;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ChunkyMapTile&& ((ChunkyMapTile) o).tx == this.tx && ((ChunkyMapTile) o).ty == this.ty && this.perspective == ((ChunkyMapTile) o).perspective && ((ChunkyMapTile) o).world == this.world && ((ChunkyMapTile) o).boostzoom == this.boostzoom;
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
    return false;
  }

  @Override
  protected String saveTileData() {
    return String.format("%d,%d,%s,%d", this.tx, this.ty, this.perspective.getName(), this.boostzoom);
  }
}
