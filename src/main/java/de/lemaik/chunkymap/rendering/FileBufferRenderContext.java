package de.lemaik.chunkymap.rendering;


import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.lang.reflect.Field;

import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.main.ChunkyOptions;
import se.llbit.chunky.renderer.RenderContext;

/**
 * A mocked {@link RenderContext} for Chunky that saves scene files into buffers. Only supports
 * saving scenes.
 */
public class FileBufferRenderContext extends RenderContext {

  private ByteArrayOutputStream scene;
  private ByteArrayOutputStream octree;

  public FileBufferRenderContext() {
    super(new Chunky(ChunkyOptions.getDefaults()));
    try {
      Field headless  = Chunky.class.getDeclaredField("headless");
      headless.setAccessible(true);
      headless.set(this.getChunky(), true);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public OutputStream getSceneFileOutputStream(String fileName) throws FileNotFoundException {
    if (fileName.endsWith(".json")) {
      return scene = new ByteArrayOutputStream();
    } else if (fileName.endsWith(".octree2")) {
      return octree = new ByteArrayOutputStream();
    } else {
      return new OutputStream() {
        @Override
        public void write(int b) {
          // no-op
        }
      };
    }
  }

  public byte[] getScene() {
    return scene.toByteArray();
  }

  public byte[] getOctree() {
    return octree.toByteArray();
  }

  public void setRenderThreadCount(int threads) {
    config.renderThreads = threads;
  }

  public void dispose() {
    scene = null;
    octree = null;
  }
}
