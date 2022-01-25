package de.lemaik.chunkymap.rendering.local;

import de.lemaik.chunky.denoiser.AlbedoTracer;
import de.lemaik.chunky.denoiser.CombinedRayTracer;
import de.lemaik.chunky.denoiser.NormalTracer;
import de.lemaik.chunkymap.ChunkyMapPlugin;
import de.lemaik.chunkymap.rendering.FileBufferRenderContext;
import de.lemaik.chunkymap.rendering.RenderException;
import de.lemaik.chunkymap.rendering.Renderer;
import de.lemaik.chunkymap.rendering.SilentTaskTracker;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.time4tea.oidn.Oidn;
import net.time4tea.oidn.Oidn.DeviceType;
import net.time4tea.oidn.OidnDevice;
import net.time4tea.oidn.OidnFilter;
import net.time4tea.oidn.OidnImages;
import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.renderer.Postprocess;
import se.llbit.chunky.renderer.RenderManager;
import se.llbit.chunky.renderer.SnapshotControl;
import se.llbit.chunky.renderer.scene.PathTracer;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.renderer.scene.SynchronousSceneManager;
import se.llbit.chunky.resources.BitmapImage;
import se.llbit.chunky.resources.TexturePackLoader;
import se.llbit.util.TaskTracker;

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

  private String getTexturepackPaths(File texturepack) {
    if (defaultTexturepack != null) {
      if (texturepack != null) {
        return texturepack.getAbsolutePath() + File.pathSeparator + defaultTexturepack
            .getAbsolutePath();
      } else {
        return defaultTexturepack.getAbsolutePath();
      }
    } else if (texturepack != null) {
      return texturepack.getAbsolutePath();
    }
    return "";
  }

  @Override
  public CompletableFuture<BufferedImage> render(FileBufferRenderContext context, File texturepack,
      Consumer<Scene> initializeScene) {
    CompletableFuture<BufferedImage> result = new CompletableFuture<>();

    String texturepackPaths = this.getTexturepackPaths(texturepack);
    if (!texturepackPaths.equals(previousTexturepacks)) {
      TexturePackLoader.loadTexturePacks(texturepackPaths, false);
      previousTexturepacks = texturepackPaths;
    }

    CombinedRayTracer combinedRayTracer = new CombinedRayTracer();
    context.getChunky().setRayTracerFactory(() -> combinedRayTracer);
    context.setRenderThreadCount(threads);
    RenderManager renderer = new RenderManager(context, false);
    renderer.setCPULoad(cpuLoad);

    SynchronousSceneManager sceneManager = new SynchronousSceneManager(context, renderer);
    initializeScene.accept(sceneManager.getScene());
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

    AtomicReference<FloatBuffer> albedo = new AtomicReference<>();
    AtomicReference<FloatBuffer> normal = new AtomicReference<>();

    renderer.setOnRenderCompleted((time, sps) -> {
      try {
        if (combinedRayTracer.getRayTracer() instanceof PathTracer) {
          result.complete(getImage(sceneManager.getScene(), albedo.get(), normal.get()));
          renderer.interrupt();
        } else if (combinedRayTracer.getRayTracer() instanceof AlbedoTracer) {
          Scene scene = renderer.getBufferedScene();
          double[] samples = scene.getSampleBuffer();
          FloatBuffer albedoBuffer = Oidn.Companion.allocateBuffer(sceneManager.getScene().width,
              sceneManager.getScene().height);
          for (int y = 0; y < scene.height; y++) {
            for (int x = 0; x < scene.width; x++) {
              albedoBuffer.put((y * scene.width + x) * 3,
                  (float) Math.min(1.0, samples[(y * scene.width + x) * 3 + 0]));
              albedoBuffer.put((y * scene.width + x) * 3 + 1,
                  (float) Math.min(1.0, samples[(y * scene.width + x) * 3 + 1]));
              albedoBuffer.put((y * scene.width + x) * 3 + 2,
                  (float) Math.min(1.0, samples[(y * scene.width + x) * 3 + 2]));
            }
          }
          albedo.set(albedoBuffer);

          combinedRayTracer.setRayTracer(new PathTracer());
          sceneManager.getScene().haltRender();
          sceneManager.getScene().setTargetSpp(targetSpp);
          sceneManager.getScene().startHeadlessRender();
        } else if (combinedRayTracer.getRayTracer() instanceof NormalTracer) {
          Scene scene = renderer.getBufferedScene();
          double[] samples = scene.getSampleBuffer();
          FloatBuffer normalBuffer = Oidn.Companion.allocateBuffer(sceneManager.getScene().width,
              sceneManager.getScene().height);
          for (int y = 0; y < scene.height; y++) {
            for (int x = 0; x < scene.width; x++) {
              normalBuffer.put((y * scene.width + x) * 3,
                  (float) samples[(y * scene.width + x) * 3 + 0]);
              normalBuffer.put((y * scene.width + x) * 3 + 1,
                  (float) samples[(y * scene.width + x) * 3 + 1]);
              normalBuffer.put((y * scene.width + x) * 3 + 2,
                  (float) samples[(y * scene.width + x) * 3 + 2]);
            }
          }
          normal.set(normalBuffer);

          combinedRayTracer.setRayTracer(new AlbedoTracer());
          sceneManager.getScene().haltRender();
          sceneManager.getScene().setTargetSpp(albedoTargetSpp);
          sceneManager.getScene().startHeadlessRender();
        }
      } catch (ReflectiveOperationException e) {
        result
            .completeExceptionally(new RenderException("Could not get final image from Chunky", e));
      }
    });

    try {
      if (enableDenoiser && normalTargetSpp > 0) {
        combinedRayTracer.setRayTracer(new NormalTracer());
        sceneManager.getScene().setTargetSpp(normalTargetSpp);
      } else if (enableDenoiser && albedoTargetSpp > 0) {
        combinedRayTracer.setRayTracer(new AlbedoTracer());
        sceneManager.getScene().setTargetSpp(albedoTargetSpp);
      } else {
        sceneManager.getScene().setTargetSpp(targetSpp);
      }

      sceneManager.getScene().startHeadlessRender();
      renderer.start();
      renderer.join();
      renderer.shutdown();
    } catch (InterruptedException e) {
      result.completeExceptionally(new RenderException("Rendering failed", e));
    } finally {
      renderer.shutdown();
    }

    return result;
  }

  private BufferedImage getImage(Scene scene, FloatBuffer albedo, FloatBuffer normal)
      throws ReflectiveOperationException {
    if (enableDenoiser) {
      double[] samples = scene.getSampleBuffer();
      FloatBuffer buffer = Oidn.Companion.allocateBuffer(scene.width, scene.height);

      // TODO use multiple threads for post-processing
      for (int y = 0; y < scene.height; y++) {
        for (int x = 0; x < scene.width; x++) {
          double[] result = new double[3];
          if (scene.getPostprocess() != Postprocess.NONE) {
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

      Oidn oidn = new Oidn();
      try (OidnDevice device = oidn.newDevice(DeviceType.DEVICE_TYPE_DEFAULT)) {
        try (OidnFilter filter = device.raytraceFilter()) {
          filter.setFilterImage(buffer, buffer, scene.width, scene.height);
          if (albedo != null) {
            // albedo is required if normal is set
            filter.setAdditionalImages(albedo, normal, scene.width, scene.height);
          }
          filter.commit();
          filter.execute();
        }
      }

      BufferedImage renderedImage = OidnImages.Companion
          .newBufferedImage(scene.width, scene.height);
      for (int i = 0; i < buffer.capacity(); i++) {
        renderedImage.getRaster().getDataBuffer().setElemFloat(i, buffer.get(i));
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
          .getDeclaredMethod("computeAlpha", new Class[]{TaskTracker.class, int.class});
      computeAlpha.setAccessible(true);
      computeAlpha.invoke(scene, SilentTaskTracker.INSTANCE, threads);

      Field finalized = sceneClass.getDeclaredField("finalized");
      finalized.setAccessible(true);
      if (!finalized.getBoolean(scene)) {
        scene.postProcessFrame(SilentTaskTracker.INSTANCE, threads);
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
