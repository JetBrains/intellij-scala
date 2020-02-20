package org.jetbrains.plugins.scala.editor;

import com.intellij.CommonBundle;
import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

/**
 * @author Ksenia.Sautina
 * @since 4/24/12
 */
public class EditorBundle extends DynamicBundle {
  @NonNls
  private static final String BUNDLE = "messages.ScalaEditorBundle";

  private static final EditorBundle INSTANCE = new EditorBundle();

  private EditorBundle() {
    super(BUNDLE);
  }

  @Nls
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}
