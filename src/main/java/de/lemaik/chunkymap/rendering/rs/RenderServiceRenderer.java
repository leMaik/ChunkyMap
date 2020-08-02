package de.lemaik.chunkymap.rendering.rs;

import de.lemaik.chunkymap.rendering.FileBufferRenderContext;
import de.lemaik.chunkymap.rendering.RenderException;
import de.lemaik.chunkymap.rendering.Renderer;
import de.lemaik.chunkymap.rendering.SilentTaskTracker;
import se.llbit.chunky.renderer.scene.Scene;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * A renderer that uses the RenderService.
 */
public class RenderServiceRenderer implements Renderer {
    private static final String RS3_API_URL = "https://api.rs.wertarbyte.com";
    private static final ApiClient API_CLIENT = new ApiClient(RS3_API_URL);

    @Override
    public CompletableFuture<BufferedImage> render(FileBufferRenderContext context, File texturepack, Consumer<Scene> initializeScene) {
        CompletableFuture<BufferedImage> result = new CompletableFuture<>();

        Scene scene = new Scene();
        initializeScene.accept(scene);
        try {
            scene.saveScene(context, SilentTaskTracker.INSTANCE);
        } catch (IOException | InterruptedException e) {
            result.completeExceptionally(new RenderException("Error while creating scene files", e));
            return result;
        }

        final RenderJob job;
        try {
            job = new ApiClient(RS3_API_URL).createJob(
                    context.getScene(),
                    context.getOctree(),
                    context.getGrass(),
                    context.getFoliage(),
                    null, null,
                    SilentTaskTracker.INSTANCE
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            result.completeExceptionally(new RenderException("Could not create the job", e));
            return result;
        }

        new Thread(() -> {
            try {
                while (API_CLIENT.getJob(job.getId()).get().getSpp() < job.getTargetSpp()) {
                    Thread.sleep(5000);
                }
                result.complete(API_CLIENT.getPicture(job.getId()));
            } catch (Exception e) {
                result.completeExceptionally(new RenderException("Exception while waiting for the RenderService to render the image", e));
            }
        }).start();

        return result;
    }

    @Override
    public void setDefaultTexturepack(File texturepack) {
        throw new UnsupportedOperationException();
    }

}
