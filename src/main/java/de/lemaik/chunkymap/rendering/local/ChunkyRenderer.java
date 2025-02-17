package de.lemaik.chunkymap.rendering.local;

// import de.lemaik.chunky.denoiser.DenoisedPathTracingRenderer;
// import de.lemaik.chunky.denoiser.DenoiserSettings;

import de.lemaik.chunkymap.ChunkyMapPlugin;
import de.lemaik.chunkymap.rendering.FileBufferRenderContext;
import de.lemaik.chunkymap.rendering.RenderException;
import de.lemaik.chunkymap.rendering.Renderer;
import de.lemaik.chunkymap.rendering.SilentTaskTracker;
import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.renderer.RenderManager;
import se.llbit.chunky.renderer.SnapshotControl;
import se.llbit.chunky.renderer.scene.AlphaBuffer;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.renderer.scene.SynchronousSceneManager;
import se.llbit.chunky.resources.BitmapImage;
import se.llbit.chunky.resources.ResourcePackLoader;
import se.llbit.util.TaskTracker;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A renderer that uses Chunky to render scenes locally.
 */
public class ChunkyRenderer implements Renderer {

  private static List<File> previousTexturepacks;
  private File defaultTexturepack;
  private final int targetSpp;
  private final boolean enableDenoiser;
  private final int albedoTargetSpp;
  private final int normalTargetSpp;
  private final int threads;
  private final int cpuLoad;

  static {
    // TODO fix denoiser
    /*
    Chunky.addRenderer(new DenoisedPathTracingRenderer(
            new DenoiserSettings(), new Oidn4jDenoiser(),
            "DenoisedPathTracer", "DenoisedPathTracer", "DenoisedPathTracer", new PathTracer()));
     */
  }

  public ChunkyRenderer(int targetSpp, boolean enableDenoiser, int albedoTargetSpp,
      int normalTargetSpp, int threads, int cpuLoad) {
    this.targetSpp = targetSpp;
    this.enableDenoiser = enableDenoiser;
    this.albedoTargetSpp = albedoTargetSpp;
    this.normalTargetSpp = normalTargetSpp;
    this.threads = threads;
    this.cpuLoad = cpuLoad;

    PersistentSettings.changeSettingsDirectory(
        new File(ChunkyMapPlugin.getPlugin(ChunkyMapPlugin.class).getDataFolder(), "chunky"));
    PersistentSettings.setLoadPlayers(false);
    PersistentSettings.setDisableDefaultTextures(true);
  }

  @Override
  public void setDefaultTexturepack(File texturepack) {
    defaultTexturepack = texturepack;
  }

  @Override
  public CompletableFuture<BufferedImage> render(FileBufferRenderContext context, File[] texturepacks,
      Consumer<Scene> initializeScene) {
    CompletableFuture<BufferedImage> result = new CompletableFuture<>();

    List<File> resourcepacks = new ArrayList<>(texturepacks.length);
    if (defaultTexturepack != null) {
      resourcepacks.add(defaultTexturepack);
    }
    if (!resourcepacks.equals(previousTexturepacks)) {
      ResourcePackLoader.loadResourcePacks(resourcepacks);
      previousTexturepacks = resourcepacks;
    }

    context.setRenderThreadCount(threads);
    RenderManager renderManager = context.getChunky().getRenderController().getRenderManager();
    renderManager.setCPULoad(cpuLoad);

    SynchronousSceneManager sceneManager = new SynchronousSceneManager(context, renderManager);
    initializeScene.accept(sceneManager.getScene());
    if (enableDenoiser) {
      // TODO
      // sceneManager.getScene().setRenderer("DenoisedPathTracer");
    }
    renderManager.setSceneProvider(sceneManager);
    renderManager.setSnapshotControl(new SnapshotControl() {
      @Override
      public boolean saveSnapshot(Scene scene, int nextSpp) {
        return false;
      }

      @Override
      public boolean saveRenderDump(Scene scene, int nextSpp) {
        return false;
      }
    });

    try {
      sceneManager.getScene().setTargetSpp(targetSpp);
      sceneManager.getScene().startRender();
      renderManager.start();
      renderManager.join();
      result.complete(getImage(sceneManager.getScene()));
    } catch (InterruptedException | ReflectiveOperationException e) {
      result.completeExceptionally(new RenderException("Rendering failed", e));
    } finally {
      renderManager.shutdown();
    }

    return result;
  }

  private BufferedImage getImage(Scene scene)
      throws ReflectiveOperationException {
    Class<Scene> sceneClass = Scene.class;
    Method computeAlpha = AlphaBuffer.class
        .getDeclaredMethod("computeAlpha", new Class[]{Scene.class, AlphaBuffer.Type.class, TaskTracker.class});
    computeAlpha.setAccessible(true);
    computeAlpha.invoke(scene.getAlphaBuffer(), new Object[]{scene, AlphaBuffer.Type.UINT8, TaskTracker.NONE});

    Field finalized = sceneClass.getDeclaredField("finalized");
    finalized.setAccessible(true);
    if (!finalized.getBoolean(scene)) {
      scene.postProcessFrame(SilentTaskTracker.INSTANCE);
    }

    Field backBuffer = sceneClass.getDeclaredField("backBuffer");
    backBuffer.setAccessible(true);
    BitmapImage bitmap = (BitmapImage) backBuffer.get(scene);

    BufferedImage renderedImage = new BufferedImage(bitmap.width, bitmap.height,
        BufferedImage.TYPE_INT_ARGB);
    DataBufferInt dataBuffer = (DataBufferInt) renderedImage.getRaster().getDataBuffer();
    int[] data = dataBuffer.getData();
    System.arraycopy(bitmap.data, 0, data, 0, bitmap.width * bitmap.height);

    return renderedImage;
  }
}
