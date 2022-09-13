package org.jetbrains.plugins.scala.build;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class CompilerSharedBuildBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.CompilerSharedBuildBundle";

    private static final CompilerSharedBuildBundle INSTANCE = new CompilerSharedBuildBundle();

    private CompilerSharedBuildBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
