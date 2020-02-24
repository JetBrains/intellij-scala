package org.jetbrains.plugins.scala.codeInspection;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author Ksenia.Sautina
 * @since 4/12/12
 */
public class InspectionBundle extends DynamicBundle {
  @NonNls
  private static final String BUNDLE = "org.jetbrains.plugins.scala.codeInspection.InspectionBundle";

  private static final InspectionBundle INSTANCE = new InspectionBundle();

  private InspectionBundle() {
    super(BUNDLE);
  }

  @Nls
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}