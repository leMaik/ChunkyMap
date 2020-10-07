package de.lemaik.chunkymap.rendering.rs;

import de.lemaik.chunky.denoiser.CombinedRayTracer;
import de.lemaik.chunkymap.rendering.FileBufferRenderContext;
import de.lemaik.chunkymap.rendering.Renderer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import se.llbit.chunky.renderer.RenderManager;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.renderer.scene.SynchronousSceneManager;
import se.llbit.util.ProgressListener;
import se.llbit.util.TaskTracker;

public class RemoteRenderer implements Renderer {

  private final ApiClient api = new ApiClient("https://api.chunkycloud.lemaik.de");
  private final int samplesPerPixel;

  public RemoteRenderer(int samplesPerPixel) {
    this.samplesPerPixel = samplesPerPixel;
  }

  @Override
  public CompletableFuture<BufferedImage> render(FileBufferRenderContext context, File texturepack,
      Consumer<Scene> initializeScene) throws IOException {
    CombinedRayTracer combinedRayTracer = new CombinedRayTracer();
    context.getChunky().setRayTracerFactory(() -> combinedRayTracer);

    Scene scene = context.getChunky().getSceneFactory().newScene();
    initializeScene.accept(scene);
    scene.saveScene(context, new TaskTracker(ProgressListener.NONE));

    RenderJob job = null;
    try {
      job = api.createJob(context.getScene(), context.getOctree(), null, null, samplesPerPixel,
          new TaskTracker(ProgressListener.NONE)).get();
      api.waitForCompletion(job).get();
      return CompletableFuture.completedFuture(api.getPicture(job.getId()));
    } catch (InterruptedException | ExecutionException e) {
      throw new IOException("Rendering failed", e);
    }
  }

  @Override
  public void setDefaultTexturepack(File texturepack) {
    // no-op
  }
}
