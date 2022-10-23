package de.lemaik.chunkymap.rendering;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import se.llbit.chunky.renderer.scene.Scene;

/**
 * A renderer that can render an image of a {@link se.llbit.chunky.renderer.scene.Scene}.
 */
public interface Renderer {

  /**
   * Renders a scene using the given context.
   *
   * @param context         render context
   * @param texturepacks     the texturepacks
   * @param initializeScene function that initializes the scene
   * @return future with the rendered image
   */
  CompletableFuture<BufferedImage> render(FileBufferRenderContext context, File[] texturepacks,
      Consumer<Scene> initializeScene)
      throws IOException;

  /**
   * set the default / fallback texturepack to use
   *
   * @param texturepack
   */
  void setDefaultTexturepack(File texturepack);
}
