package org.jetbrains.plugins.scala.structuralSearch;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class ScalaStructuralSearchBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaStructuralSearchBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(ScalaStructuralSearchBundle.class, BUNDLE);

    private ScalaStructuralSearchBundle() {
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
