package de.lemaik.chunkymap.rendering.local;

import java.util.logging.Logger;
import se.llbit.log.Level;
import se.llbit.log.Receiver;

/**
 * Adapter for Chunky's logger that redirects log messages to the plugin's logger and suppresses
 * known false-positive warnings.
 */
public class ChunkyLogAdapter extends Receiver {

  private Logger logger;

  public ChunkyLogAdapter(Logger logger) {
    this.logger = logger;
  }

  @Override
  public void logEvent(Level level, String message) {
    if (this.shouldIgnoreMessage(level, message, null)) {
      return;
    }
    switch (level) {
      case ERROR:
        logger.severe(message);
        break;
      case WARNING:
        logger.warning(message);
        break;
      case INFO:
      default:
        logger.info(message);
        break;
    }
  }

  @Override
  public void logEvent(Level level, String message, Throwable thrown) {
    if (this.shouldIgnoreMessage(level, message, thrown)) {
      return;
    }
    switch (level) {
      case ERROR:
        logger.log(java.util.logging.Level.SEVERE, message, thrown);
        break;
      case WARNING:
        logger.log(java.util.logging.Level.WARNING, message, thrown);
        break;
      case INFO:
      default:
        logger.log(java.util.logging.Level.INFO, message, thrown);
        break;
    }
  }

  @Override
  public void logEvent(Level level, Throwable thrown) {
    if (this.shouldIgnoreMessage(level, null, thrown)) {
      return;
    }
    switch (level) {
      case ERROR:
        logger.log(java.util.logging.Level.SEVERE, thrown.getMessage(), thrown);
        break;
      case WARNING:
        logger.log(java.util.logging.Level.WARNING, thrown.getMessage(), thrown);
        break;
      case INFO:
      default:
        logger.log(java.util.logging.Level.INFO, thrown.getMessage(), thrown);
    }
  }

  protected boolean shouldIgnoreMessage(Level level, String message, Throwable thrown) {
    if (message == null) {
      return false;
    }
    if (message.startsWith("Warning: Could not load settings from")) {
      // this is intended
      return true;
    }
    if (message.startsWith("Unknown biome")) {
      // don't spam the user about this
      return true;
    }
    return false;
  }
}
