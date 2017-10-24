package org.jetbrains.sbt.project.modifier.ui;

import com.intellij.openapi.vcs.FileStatus;

/**
 * @author Roman.Shein
 * @since 20.03.2015.
 */
public enum BuildFileModifiedStatus {
  DETECTED, MODIFIED_AUTOMATICALLY, MODIFIED_MANUALLY, MODIFIED_BOTH;

  public FileStatus getChangeStatus() {
    return this == DETECTED ? FileStatus.NOT_CHANGED : FileStatus.MODIFIED;
  }

  public String getOriginText() {
    switch (this) {
      case DETECTED: return "Detected";
      case MODIFIED_AUTOMATICALLY: return "Modified automatically";
      case MODIFIED_MANUALLY: return "Modified manually";
      case MODIFIED_BOTH: return "Modified automatically and fixed manually";
      default: throw new RuntimeException("Unexpected build file status: " + this);
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
