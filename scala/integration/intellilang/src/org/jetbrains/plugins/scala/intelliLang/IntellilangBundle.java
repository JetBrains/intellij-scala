package org.jetbrains.plugins.scala.intelliLang;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class IntellilangBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaIntellilangBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(IntellilangBundle.class, BUNDLE);

    private IntellilangBundle() {
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
