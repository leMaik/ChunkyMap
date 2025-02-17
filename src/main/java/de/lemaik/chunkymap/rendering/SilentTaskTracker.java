package de.lemaik.chunkymap.rendering;

import se.llbit.util.ProgressListener;
import se.llbit.util.TaskTracker;

import java.time.Duration;

public class SilentTaskTracker extends TaskTracker {

  public static final TaskTracker INSTANCE = new SilentTaskTracker();

  private SilentTaskTracker() {
    super(new ProgressListener() {
      @Override
      public void setProgress(String task, int done, int start, int target, Duration elapsedTime) {
        // empty
      }

      @Override
      public void setProgress(String task, int done, int start, int target, Duration elapsedTime, Duration remainingTime) {
        // empty
      }
    });
  }
}
