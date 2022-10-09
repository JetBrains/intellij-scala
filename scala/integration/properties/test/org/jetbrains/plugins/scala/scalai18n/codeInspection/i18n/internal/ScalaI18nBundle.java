package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class ScalaI18nBundle extends DynamicBundle {
  @NonNls
  private static final String BUNDLE = "messages.ScalaI18nBundle";

  private static final ScalaI18nBundle INSTANCE = new ScalaI18nBundle();

  private ScalaI18nBundle() {
    super(BUNDLE);
  }

  @Nls
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}