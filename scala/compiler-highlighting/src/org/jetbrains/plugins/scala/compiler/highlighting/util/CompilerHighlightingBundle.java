package org.jetbrains.plugins.scala.compiler.highlighting.util;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class CompilerHighlightingBundle {
    @NonNls
    public static final String BUNDLE = "messages.CompilerHighlightingBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(CompilerHighlightingBundle.class, BUNDLE);

    private CompilerHighlightingBundle() {
    }

    @NotNull
    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
