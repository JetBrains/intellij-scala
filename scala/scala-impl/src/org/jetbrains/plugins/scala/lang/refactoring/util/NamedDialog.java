package org.jetbrains.plugins.scala.lang.refactoring.util;

import org.jetbrains.annotations.Nullable;

public interface NamedDialog {
  public boolean isReplaceAllOccurrences();

  @Nullable
  public String getEnteredName();
}
