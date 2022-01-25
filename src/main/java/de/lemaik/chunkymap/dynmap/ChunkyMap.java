package de.lemaik.chunkymap.dynmap;

import de.lemaik.chunkymap.ChunkyMapPlugin;
import de.lemaik.chunkymap.rendering.Renderer;
import de.lemaik.chunkymap.rendering.local.ChunkyRenderer;
import de.lemaik.chunkymap.rendering.rs.RemoteRenderer;
import de.lemaik.chunkymap.util.MinecraftDownloader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.Okio;
import org.bukkit.Bukkit;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.hdmap.HDMap;
import org.dynmap.hdmap.HDPerspective;
import org.dynmap.hdmap.IsoHDPerspective;
import org.dynmap.utils.TileFlags;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.json.JsonNumber;
import se.llbit.json.JsonObject;
import se.llbit.json.JsonParser;

/**
 * A map that uses the RenderService for rendering the tiles.
 */
public class ChunkyMap extends HDMap {

  private static final String DEFAULT_TEXTUREPACK_VERSION = "1.16.2";
  public final DynmapCameraAdapter cameraAdapter;
  private final Renderer renderer;
  private File defaultTexturepackPath;
  private File texturepackPath;
  private File worldPath;
  private final Object worldPathLock = new Object();
  private JsonObject templateScene;
  private final int chunkPadding;
  private final boolean requeueFailedTiles;

  public ChunkyMap(DynmapCore dynmap, ConfigurationNode config) {
    super(dynmap, config);
    cameraAdapter = new DynmapCameraAdapter((IsoHDPerspective) getPerspective());
    if (config.getBoolean("chunkycloud/enabled", false)) {
      renderer = new RemoteRenderer(config.getString("chunkycloud/apiKey", ""),
          config.getInteger("samplesPerPixel", 100),
          config.getString("texturepack", null),
          config.getBoolean("chunkycloud/initializeLocally", true));
      if (config.getString("chunkycloud/apiKey", "").isEmpty()) {
        ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class).getLogger()
            .warning("No ChunkyCloud API Key configured.");
      }
    } else {
      renderer = new ChunkyRenderer(
          config.getInteger("samplesPerPixel", 100),
          config.getBoolean("denoiser/enabled", false),
          config.getInteger("denoiser/albedoSamplesPerPixel", 16),
          config.getInteger("denoiser/normalSamplesPerPixel", 16),
          config.getInteger("chunkyThreads", 2),
          Math.min(100, Math.max(0, config.getInteger("chunkyCpuLoad", 100)))
      );
    }
    chunkPadding = config.getInteger("chunkPadding", 0);
    requeueFailedTiles = config.getBoolean("requeueFailedTiles", true);

    String texturepackVersion = config.getString("texturepackVersion", DEFAULT_TEXTUREPACK_VERSION);
    File texturepackPath = new File(
        ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class).getDataFolder(),
        texturepackVersion + ".jar");
    if (texturepackPath.exists()) {
      defaultTexturepackPath = texturepackPath;
    } else {
      ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class).getLogger()
          .info("Downloading additional textures for Minecraft " + texturepackVersion);
      try (
          Response response = MinecraftDownloader.downloadMinecraft(texturepackVersion).get();
          ResponseBody body = response.body();
          BufferedSink sink = Okio.buffer(Okio.sink(texturepackPath))
      ) {
        sink.writeAll(body.source());
        defaultTexturepackPath = texturepackPath;
      } catch (IOException | ExecutionException | InterruptedException e) {
        ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class).getLogger()
            .log(Level.SEVERE,
                "Downloading the textures failed, your Chunky dynmap might look bad!", e);
      }
    }

    if (config.containsKey("texturepack")) {
      this.texturepackPath = Bukkit.getPluginManager().getPlugin("dynmap").getDataFolder().toPath()
          .resolve(config.getString("texturepack"))
          .toFile();
    } else {
      ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class).getLogger()
          .warning("You didn't specify a texturepack for a map that is rendered with Chunky. " +
              "The Minecraft " + texturepackVersion + " textures will be used.");
    }

    if (config.containsKey("templateScene")) {
      try (InputStream inputStream = new FileInputStream(
          Bukkit.getPluginManager().getPlugin("dynmap").getDataFolder().toPath()
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
  }

  @Override
  public void addMapTiles(List<MapTile> list, DynmapWorld world, int tx, int ty) {
    list.add(new ChunkyMapTile(world, getPerspective(), tx, ty, getBoostZoom()));
  }

  public List<TileFlags.TileCoord> getTileCoords(DynmapWorld world, int x, int y, int z) {
    return getPerspective().getTileCoords(world, x, y, z);
  }

  public List<TileFlags.TileCoord> getTileCoords(DynmapWorld world, int minx, int miny, int minz,
      int maxx, int maxy, int maxz) {
    return getPerspective().getTileCoords(world, minx, miny, minz, maxx, maxy, maxz);
  }

  @Override
  public MapTile[] getAdjecentTiles(MapTile tile) {
    return getAdjecentTilesOfTile(tile, getPerspective());
  }

  public static MapTile[] getAdjecentTilesOfTile(MapTile tile, HDPerspective perspective) {
    ChunkyMapTile t = (ChunkyMapTile) tile;
    DynmapWorld w = t.getDynmapWorld();
    int x = t.tileOrdinalX();
    int y = t.tileOrdinalY();

    return new MapTile[]{
        new ChunkyMapTile(w, perspective, x - 1, y - 1, t.boostzoom),
        new ChunkyMapTile(w, perspective, x - 1, y + 1, t.boostzoom),
        new ChunkyMapTile(w, perspective, x + 1, y - 1, t.boostzoom),
        new ChunkyMapTile(w, perspective, x + 1, y + 1, t.boostzoom),
        new ChunkyMapTile(w, perspective, x, y - 1, t.boostzoom),
        new ChunkyMapTile(w, perspective, x + 1, y, t.boostzoom),
        new ChunkyMapTile(w, perspective, x, y + 1, t.boostzoom),
        new ChunkyMapTile(w, perspective, x - 1, y, t.boostzoom)};
  }

  @Override
  public List<MapType> getMapsSharingRender(DynmapWorld world) {
    ArrayList<MapType> maps = new ArrayList<>();
    for (MapType mt : world.maps) {
      if (mt instanceof ChunkyMap) {
        ChunkyMap chunkyMap = (ChunkyMap) mt;
        if (chunkyMap.getPerspective() == getPerspective()
            && chunkyMap.getBoostZoom() == getBoostZoom()) {
          maps.add(mt);
        }
      }
    }
    return maps;
  }

  @Override
  public List<String> getMapNamesSharingRender(DynmapWorld dynmapWorld) {
    return getMapsSharingRender(dynmapWorld).stream().map(MapType::getName)
        .collect(Collectors.toList());
  }

  Renderer getRenderer() {
    return renderer;
  }

  File getDefaultTexturepackPath() {
    return defaultTexturepackPath;
  }

  File getTexturepackPath() {
    return texturepackPath;
  }

  int getChunkPadding() {
    return chunkPadding;
  }

  public boolean getRequeueFailedTiles() {
    return requeueFailedTiles;
  }

  void applyTemplateScene(Scene scene) {
    if (this.templateScene != null) {
      scene.importFromJson(templateScene);
    }
  }

  File getWorldFolder(DynmapWorld world) {
    if (worldPath == null) {
      // Fixes a ConcurrentModificationException, see https://github.com/leMaik/ChunkyMap/issues/30
      synchronized (worldPathLock) {
        worldPath = Bukkit.getWorld(world.getRawName()).getWorldFolder();
      }
    }
    return worldPath;
  }
}
