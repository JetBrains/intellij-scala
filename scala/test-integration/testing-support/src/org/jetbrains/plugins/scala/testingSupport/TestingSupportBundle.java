package org.jetbrains.plugins.scala.testingSupport;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class TestingSupportBundle {
    @NonNls
    private static final String BUNDLE = "messages.TestingSupportBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(TestingSupportBundle.class, BUNDLE);

    private TestingSupportBundle() {
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
