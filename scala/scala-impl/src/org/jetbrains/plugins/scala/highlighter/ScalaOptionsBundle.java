package org.jetbrains.plugins.scala.highlighter;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaOptionsBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaOptionsBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(ScalaOptionsBundle.class, BUNDLE);

    private ScalaOptionsBundle() {
    }

    @NotNull
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
