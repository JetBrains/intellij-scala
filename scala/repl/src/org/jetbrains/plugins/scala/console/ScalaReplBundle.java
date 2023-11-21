package org.jetbrains.plugins.scala.console;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaReplBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaReplBundle";

    private static final ScalaReplBundle INSTANCE = new ScalaReplBundle();

    private ScalaReplBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
