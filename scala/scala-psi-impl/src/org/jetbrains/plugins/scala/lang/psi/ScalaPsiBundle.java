package org.jetbrains.plugins.scala.lang.psi;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaPsiBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaPsiBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(ScalaPsiBundle.class, BUNDLE);

    private ScalaPsiBundle() {
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
