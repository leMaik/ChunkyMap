package de.lemaik.chunkymap.rendering.local;

import de.lemaik.chunky.denoiser.DenoisedPathTracingRenderer;
import de.lemaik.chunky.denoiser.DenoiserSettings;
import de.lemaik.chunkymap.ChunkyMapPlugin;
import de.lemaik.chunkymap.rendering.FileBufferRenderContext;
import de.lemaik.chunkymap.rendering.RenderException;
import de.lemaik.chunkymap.rendering.Renderer;
import de.lemaik.chunkymap.rendering.SilentTaskTracker;
import net.time4tea.oidn.OidnImages;
import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.renderer.RenderManager;
import se.llbit.chunky.renderer.SnapshotControl;
import se.llbit.chunky.renderer.scene.PathTracer;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.renderer.scene.SynchronousSceneManager;
import se.llbit.chunky.resources.BitmapImage;
import se.llbit.chunky.resources.TexturePackLoader;
import se.llbit.util.TaskTracker;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A renderer that uses Chunky to render scenes locally.
 */
public class ChunkyRenderer implements Renderer {

  private static String previousTexturepacks;
  private File defaultTexturepack;
  private final int targetSpp;
  private final boolean enableDenoiser;
  private final int albedoTargetSpp;
  private final int normalTargetSpp;
  private final int threads;
  private final int cpuLoad;

  static {
    Chunky.addRenderer(new DenoisedPathTracingRenderer(
            new DenoiserSettings(), new Oidn4jDenoiser(),
            "DenoisedPathTracer", "DenoisedPathTracer", "DenoisedPathTracer", new PathTracer()));
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

  private String getTexturepackPaths(File[] texturepacks) {
    StringBuilder texturepackPaths = new StringBuilder();
    for (File texturepack : texturepacks) {
      if (texturepackPaths.length() > 0) {
        texturepackPaths.append(File.pathSeparator);
      }
      texturepackPaths.append(texturepack.getAbsolutePath());
    }

    if (defaultTexturepack != null) {
      if (texturepackPaths.length() > 0) {
        texturepackPaths.append(File.pathSeparator);
      }
      texturepackPaths.append(defaultTexturepack.getAbsolutePath());
    }
    return texturepackPaths.toString();
  }

  @Override
  public CompletableFuture<BufferedImage> render(FileBufferRenderContext context, File[] texturepacks,
      Consumer<Scene> initializeScene) {
    CompletableFuture<BufferedImage> result = new CompletableFuture<>();

    String texturepackPaths = this.getTexturepackPaths(texturepacks);
    if (!texturepackPaths.equals(previousTexturepacks)) {
      TexturePackLoader.loadTexturePacks(texturepackPaths, false);
      previousTexturepacks = texturepackPaths;
    }

    context.setRenderThreadCount(threads);
    RenderManager renderManager = context.getChunky().getRenderController().getRenderManager();
    renderManager.setCPULoad(cpuLoad);

    SynchronousSceneManager sceneManager = new SynchronousSceneManager(context, renderManager);
    initializeScene.accept(sceneManager.getScene());
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
    if (enableDenoiser) {
      double[] samples = scene.getSampleBuffer();

      // TODO re-enable post-processing when denoising
      // TODO use multiple threads for post-processing
      /*
      for (int y = 0; y < scene.height; y++) {
        for (int x = 0; x < scene.width; x++) {
          double[] result = new double[3];
          if (!scene.getPostProcessingFilter().getId().equals("NONE")) {
            scene.getPostProcessingFilter().processFrame(scene.width, scene.height, result);
            scene.postProcessPixel(x, y, result);
          } else {
            result[0] = samples[(y * scene.width + x) * 3 + 0];
            result[1] = samples[(y * scene.width + x) * 3 + 1];
            result[2] = samples[(y * scene.width + x) * 3 + 2];
          }
          buffer.put((y * scene.width + x) * 3, (float) Math.min(1.0, result[0]));
          buffer.put((y * scene.width + x) * 3 + 1, (float) Math.min(1.0, result[1]));
          buffer.put((y * scene.width + x) * 3 + 2, (float) Math.min(1.0, result[2]));
        }
      }
      */

      BufferedImage renderedImage = OidnImages.Companion
          .newBufferedImage(scene.width, scene.height);
      for (int i = 0; i < samples.length; i++) {
        renderedImage.getRaster().getDataBuffer().setElemDouble(i, samples[i]);
      }
      BufferedImage imageInIntPixelLayout = new BufferedImage(scene.width, scene.height,
          BufferedImage.TYPE_INT_ARGB);
      Graphics2D graphics = imageInIntPixelLayout.createGraphics();
      graphics.drawImage(renderedImage, 0, 0, null);
      graphics.dispose();

      return imageInIntPixelLayout;
    } else {
      Class<Scene> sceneClass = Scene.class;
      Method computeAlpha = sceneClass
          .getDeclaredMethod("computeAlpha", new Class[]{TaskTracker.class});
      computeAlpha.setAccessible(true);
      computeAlpha.invoke(scene, SilentTaskTracker.INSTANCE);

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
}
