package de.lemaik.chunkymap.rendering.rs;

import de.lemaik.chunkymap.rendering.FileBufferRenderContext;
import de.lemaik.chunkymap.rendering.Renderer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.util.ProgressListener;
import se.llbit.util.TaskTracker;

public class RemoteRenderer implements Renderer {

  private final ApiClient api;
  private final int samplesPerPixel;
  private final String texturepack;
  private final boolean initializeLocally;

  public RemoteRenderer(String apiKey, int samplesPerPixel, String texturepack,
      boolean initializeLocally) {
    this.samplesPerPixel = samplesPerPixel;
    this.texturepack = texturepack;
    this.initializeLocally = initializeLocally;
    this.api = new ApiClient("https://api.chunkycloud.lemaik.de", apiKey);
  }

  public boolean shouldInitializeLocally() {
    return initializeLocally;
  }

  @Override
  public CompletableFuture<BufferedImage> render(FileBufferRenderContext context, File[] texturepacks,
      Consumer<Scene> initializeScene) throws IOException {
    Scene scene = context.getChunky().getSceneFactory().newScene();
    initializeScene.accept(scene);

    RenderJob job = null;
    try {
      if (initializeLocally) {
        job = api
            .createJob(context.getScene(), context.getOctree(), null, null,
                this.texturepack, samplesPerPixel, new TaskTracker(ProgressListener.NONE)).get();
      } else {
        job = api.createJob(context.getScene(), scene.getChunks().stream().map(
            ChunkPosition::getRegionPosition).collect(Collectors.toSet()).stream()
                .map(position -> getRegionFile(scene, position))
                .filter(File::exists)
                .collect(Collectors.toList()), null, null,
            this.texturepack, samplesPerPixel, new TaskTracker(ProgressListener.NONE)).get();
      }
      api.waitForCompletion(job, 10, TimeUnit.MINUTES).get();
      return CompletableFuture.completedFuture(api.getPicture(job.getId()));
    } catch (InterruptedException | ExecutionException e) {
      if (job != null) {
        try {
          api.cancelJob(job.getId()).get();
        } catch (InterruptedException | ExecutionException ignore) {
        }
      }
      throw new IOException("Rendering failed", e);
    }
  }

  private File getRegionFile(Scene scene, ChunkPosition position) {
    try {
      Field worldPath = Scene.class.getDeclaredField("worldPath");
      worldPath.setAccessible(true);
      Field worldDimension = Scene.class.getDeclaredField("worldDimension");
      worldDimension.setAccessible(true);
      File world = new File((String) worldPath.get(scene));
      int dimension = worldDimension.getInt(scene);
      File dimWorld = dimension == 0 ? world : new File(world, "DIM" + dimension);
      return Paths.get(dimWorld.getAbsolutePath(), "region", position.getMcaName()).toFile();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Could not get region file", e);
    }
  }

  @Override
  public void setDefaultTexturepack(File texturepack) {
    // no-op
  }
}
