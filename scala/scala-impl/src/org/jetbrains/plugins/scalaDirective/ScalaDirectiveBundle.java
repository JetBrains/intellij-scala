package org.jetbrains.plugins.scalaDirective;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaDirectiveBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaDirectiveBundle";

    private static final ScalaDirectiveBundle INSTANCE = new ScalaDirectiveBundle();

    private ScalaDirectiveBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
