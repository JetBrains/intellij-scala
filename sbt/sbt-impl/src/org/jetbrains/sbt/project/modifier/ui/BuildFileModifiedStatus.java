package org.jetbrains.sbt.project.modifier.ui;

import com.intellij.openapi.vcs.FileStatus;
import org.jetbrains.sbt.SbtBundle;

public enum BuildFileModifiedStatus {
  DETECTED, MODIFIED_AUTOMATICALLY, MODIFIED_MANUALLY, MODIFIED_BOTH;

  public FileStatus getChangeStatus() {
    return this == DETECTED ? FileStatus.NOT_CHANGED : FileStatus.MODIFIED;
  }

  public String getOriginText() {
    return switch (this) {
      case DETECTED -> SbtBundle.message("sbt.build.modified.detected");
      case MODIFIED_AUTOMATICALLY -> SbtBundle.message("sbt.build.modified.automatically");
      case MODIFIED_MANUALLY -> SbtBundle.message("sbt.build.modified.manually");
      case MODIFIED_BOTH -> SbtBundle.message("sbt.build.modified.automatically.and.fixed.manually");
    };
  }

  public BuildFileModifiedStatus changeAfterManualModification() {
    return switch (this) {
      case MODIFIED_AUTOMATICALLY, MODIFIED_BOTH -> MODIFIED_BOTH;
      default -> MODIFIED_MANUALLY;
    };
  }
}
