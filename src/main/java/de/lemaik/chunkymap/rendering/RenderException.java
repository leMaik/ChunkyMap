package de.lemaik.chunkymap.rendering;

/**
 * Exception that is thrown if rendering an image fails.
 */
public class RenderException extends Exception {

  public RenderException(String message, Throwable inner) {
    super(message, inner);
  }

  public RenderException(String message) {
    super(message);
  }
}
