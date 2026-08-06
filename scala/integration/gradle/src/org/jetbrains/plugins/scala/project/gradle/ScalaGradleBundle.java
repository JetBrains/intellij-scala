package org.jetbrains.plugins.scala.project.gradle;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaGradleBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaGradleBundle";

    private static final ScalaGradleBundle INSTANCE = new ScalaGradleBundle();

    private ScalaGradleBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
