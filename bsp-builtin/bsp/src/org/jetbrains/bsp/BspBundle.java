package org.jetbrains.bsp;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class BspBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaBspBundle";

    private static final BspBundle INSTANCE = new BspBundle();

    private BspBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
