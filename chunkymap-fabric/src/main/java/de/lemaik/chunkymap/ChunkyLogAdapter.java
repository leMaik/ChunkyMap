package de.lemaik.chunkymap;

import org.slf4j.Logger;
import se.llbit.log.Level;
import se.llbit.log.Receiver;

/**
 * Adapter for Chunky's logger that redirects log messages to the plugin's logger and suppresses
 * known false-positive warnings.
 */
public class ChunkyLogAdapter extends Receiver {
    private final Logger logger;

    public ChunkyLogAdapter(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void logEvent(Level level, String message) {
        if (this.shouldIgnoreMessage(level, message, null)) {
            logger.debug(message);
            return;
        }
        switch (level) {
            case ERROR:
                logger.error(message);
                break;
            case WARNING:
                logger.warn(message);
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
            logger.debug(message, thrown);
            return;
        }
        switch (level) {
            case ERROR:
                logger.error(message, thrown);
                break;
            case WARNING:
                logger.warn(message, thrown);
                break;
            case INFO:
            default:
                logger.info(message, thrown);
                break;
        }
    }

    @Override
    public void logEvent(Level level, Throwable thrown) {
        if (this.shouldIgnoreMessage(level, null, thrown)) {
            logger.debug(thrown.getMessage(), thrown);
            return;
        }
        switch (level) {
            case ERROR:
                logger.error(thrown.getMessage(), thrown);
                break;
            case WARNING:
                logger.warn(thrown.getMessage(), thrown);
                break;
            case INFO:
            default:
                logger.info(thrown.getMessage(), thrown);
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
        if (message.startsWith("Failed to load texture:")) {
            // don't bother the user with armor texture loading errors
            return true;
        }
        return false;
    }
}
