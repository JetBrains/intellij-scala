package org.jetbrains.plugins.scala.bsp;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class MillBspBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.MillBspBundle";

    private static final MillBspBundle INSTANCE = new MillBspBundle();

    private MillBspBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
