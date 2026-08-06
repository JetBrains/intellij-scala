package org.jetbrains.plugins.scala.editor;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaEditorBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaEditorBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(ScalaEditorBundle.class, BUNDLE);

    private ScalaEditorBundle() {
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
