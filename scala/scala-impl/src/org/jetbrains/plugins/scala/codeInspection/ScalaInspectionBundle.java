package org.jetbrains.plugins.scala.codeInspection;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaInspectionBundle {
  @NonNls
  private static final String BUNDLE = "messages.ScalaInspectionBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(ScalaInspectionBundle.class, BUNDLE);

  private ScalaInspectionBundle() {
  }

  @Nls
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}
