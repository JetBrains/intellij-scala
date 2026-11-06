package org.jetbrains.plugins.scala.lang.formatting;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaFormattingBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaFormattingBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(ScalaFormattingBundle.class, BUNDLE);

    private ScalaFormattingBundle() {
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
