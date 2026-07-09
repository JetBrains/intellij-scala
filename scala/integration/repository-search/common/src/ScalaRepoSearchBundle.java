package org.jetbrains.plugins.scala.reposearch.common;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaRepoSearchBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaRepoSearchBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(ScalaRepoSearchBundle.class, BUNDLE);

    private ScalaRepoSearchBundle() {
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
