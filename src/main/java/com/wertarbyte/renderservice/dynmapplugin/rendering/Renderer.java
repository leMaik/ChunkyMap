package com.wertarbyte.renderservice.dynmapplugin.rendering;

import se.llbit.chunky.renderer.scene.Scene;

import java.awt.image.BufferedImage;
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
     * @param initializeScene function that initializes the scene
     * @return future with the rendered image
     */
    CompletableFuture<BufferedImage> render(FileBufferRenderContext context, Consumer<Scene> initializeScene);
}
