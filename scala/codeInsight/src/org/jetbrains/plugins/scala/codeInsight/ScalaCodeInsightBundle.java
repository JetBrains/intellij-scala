package org.jetbrains.plugins.scala.codeInsight;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaCodeInsightBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaCodeInsightBundle";

    private static final ScalaCodeInsightBundle INSTANCE = new ScalaCodeInsightBundle();

    private ScalaCodeInsightBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
