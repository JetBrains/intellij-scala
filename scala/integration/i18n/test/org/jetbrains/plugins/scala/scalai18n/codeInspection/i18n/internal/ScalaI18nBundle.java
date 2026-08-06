package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaI18nBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaI18nBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(ScalaI18nBundle.class, BUNDLE);

    private ScalaI18nBundle() {
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}