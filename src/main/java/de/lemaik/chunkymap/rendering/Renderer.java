package de.lemaik.chunkymap.rendering;

import se.llbit.chunky.renderer.scene.Scene;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A renderer that can render an image of a {@link se.llbit.chunky.renderer.scene.Scene}.
 */
public interface Renderer {
    /**
     * Renders a scene using the given context.
     *
     * @param context         render context
     * @param texturepack     the texturepack
     * @param initializeScene function that initializes the scene
     * @return future with the rendered image
     */
    CompletableFuture<BufferedImage> render(FileBufferRenderContext context, File texturepack, Consumer<Scene> initializeScene);
}
