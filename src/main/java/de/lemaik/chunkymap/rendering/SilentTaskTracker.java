package de.lemaik.chunkymap.rendering;

import se.llbit.util.ProgressListener;
import se.llbit.util.TaskTracker;

public class SilentTaskTracker extends TaskTracker {

  public static final TaskTracker INSTANCE = new SilentTaskTracker();

  private SilentTaskTracker() {
    super(new ProgressListener() {
      @Override
      public void setProgress(String s, int i, int i1, int i2) {
        // empty
      }

      @Override
      public void setProgress(String s, int i, int i1, int i2, String s1) {
        // empty
      }
    });
  }
}
