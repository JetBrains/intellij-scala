package org.jetbrains.plugins.scala.lang.parser;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaParserBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaParserBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(ScalaParserBundle.class, BUNDLE);

    private ScalaParserBundle() {
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
