package org.jetbrains.plugins.scala;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaBundle";

    private static final ScalaBundle INSTANCE = new ScalaBundle();

    private ScalaBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
