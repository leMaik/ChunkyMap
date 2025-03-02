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
import java.nio.file.Path;
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
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.json.JsonNumber;
import se.llbit.json.JsonObject;
import se.llbit.json.JsonParser;

/**
 * A map that uses the RenderService for rendering the tiles.
 */
public class ChunkyMap extends HDMap {

  private static final String DEFAULT_TEXTUREPACK_VERSION;
  public final DynmapCameraAdapter cameraAdapter;
  private final Renderer renderer;
  private File defaultTexturepackPath;
  private File[] resourcepackPaths;
  private File worldPath;
  private final Object worldPathLock = new Object();
  private JsonObject templateScene;
  private final int chunkPadding;
  private final boolean requeueFailedTiles;

  static {
    String bukkitVersion = Bukkit.getServer().getVersion();
    String texturePackVersion = "1.21.4";
    int start = bukkitVersion.indexOf("(MC:");
    if (start >= 0) {
      String minecraftVersion = bukkitVersion.substring(start + 5);
      start = minecraftVersion.indexOf(")");
      if(start >= 0) {
        minecraftVersion = minecraftVersion.substring(0, start).trim();
        texturePackVersion = minecraftVersion;
      }
    }
    DEFAULT_TEXTUREPACK_VERSION = texturePackVersion;
  }

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

    Path dynmapDataPath = Bukkit.getPluginManager().getPlugin("dynmap").getDataFolder().toPath();
    if (config.containsKey("resourcepacks")) {
      this.resourcepackPaths = config.getList("resourcepacks").stream()
          .map(path -> dynmapDataPath.resolve(path.toString()).toFile()).toArray(File[]::new);
    } else if (config.containsKey("texturepack")) {
      this.resourcepackPaths = new File[] { dynmapDataPath
              .resolve(config.getString("texturepack"))
              .toFile() };
    } else {
      this.resourcepackPaths = new File[0];
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
    MapTile tile = new ChunkyMapTile(world, getPerspective(), tx, ty, getBoostZoom(), getTileScale());
    list.add(tile);
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
        new ChunkyMapTile(w, perspective, x - 1, y - 1, t.boostzoom, t.tilescale),
        new ChunkyMapTile(w, perspective, x - 1, y + 1, t.boostzoom, t.tilescale),
        new ChunkyMapTile(w, perspective, x + 1, y - 1, t.boostzoom, t.tilescale),
        new ChunkyMapTile(w, perspective, x + 1, y + 1, t.boostzoom, t.tilescale),
        new ChunkyMapTile(w, perspective, x, y - 1, t.boostzoom, t.tilescale),
        new ChunkyMapTile(w, perspective, x + 1, y, t.boostzoom, t.tilescale),
        new ChunkyMapTile(w, perspective, x, y + 1, t.boostzoom, t.tilescale),
        new ChunkyMapTile(w, perspective, x - 1, y, t.boostzoom, t.tilescale)};
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

  File[] getResourcepackPaths() {
    return resourcepackPaths;
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

  private static final ImageVariant[] variants = new ImageVariant[]{ImageVariant.STANDARD};
  @Override
  public ImageVariant[] getVariants() {
    return variants;
  }
}
