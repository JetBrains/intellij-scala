package org.jetbrains.plugins.scala.testingSupport.test.munit;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class MUnitTestingSupportBundle {
    @NonNls
    public static final String BUNDLE = "messages.MUnitTestingSupportBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(MUnitTestingSupportBundle.class, BUNDLE);

    private MUnitTestingSupportBundle() {
    }

    @NotNull
    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
