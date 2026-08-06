package org.jetbrains.sbt;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class SbtApiBundle {
    @NonNls
    private static final String BUNDLE = "messages.SbtApiBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(SbtApiBundle.class, BUNDLE);

    private SbtApiBundle() {
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
