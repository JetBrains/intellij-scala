package org.jetbrains.scalaCli;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaCliBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaCliBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(ScalaCliBundle.class, BUNDLE);

    private ScalaCliBundle() {
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
