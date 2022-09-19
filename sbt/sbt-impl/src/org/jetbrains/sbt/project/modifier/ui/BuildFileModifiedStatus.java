package org.jetbrains.sbt.project.modifier.ui;

import com.intellij.openapi.vcs.FileStatus;
import org.jetbrains.sbt.SbtBundle;

public enum BuildFileModifiedStatus {
  DETECTED, MODIFIED_AUTOMATICALLY, MODIFIED_MANUALLY, MODIFIED_BOTH;

  public FileStatus getChangeStatus() {
    return this == DETECTED ? FileStatus.NOT_CHANGED : FileStatus.MODIFIED;
  }

  public String getOriginText() {
    switch (this) {
      case DETECTED: return SbtBundle.message("sbt.build.modified.detected");
      case MODIFIED_AUTOMATICALLY: return SbtBundle.message("sbt.build.modified.automatically");
      case MODIFIED_MANUALLY: return SbtBundle.message("sbt.build.modified.manually");
      case MODIFIED_BOTH: return SbtBundle.message("sbt.build.modified.automatically.and.fixed.manually");
      default: throw new RuntimeException(SbtBundle.message("sbt.build.modified.unexpected.file.status", this));
    }
  }

  public BuildFileModifiedStatus changeAfterManualModification() {
    switch (this) {
      case MODIFIED_AUTOMATICALLY:
      case MODIFIED_BOTH: return MODIFIED_BOTH;
      default: return MODIFIED_MANUALLY;
    }
  }
}
