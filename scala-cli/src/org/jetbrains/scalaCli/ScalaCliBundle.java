package org.jetbrains.scalaCli;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaCliBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaCliBundle";

    private static final ScalaCliBundle INSTANCE = new ScalaCliBundle();

    private ScalaCliBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
