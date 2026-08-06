package org.jetbrains.sbt;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class SbtApiBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.SbtApiBundle";

    private static final SbtApiBundle INSTANCE = new SbtApiBundle();

    private SbtApiBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
