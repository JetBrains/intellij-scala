package org.jetbrains.plugins.scala.conversion;


import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaConversionBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaConversionBundle";

    private static final ScalaConversionBundle INSTANCE = new ScalaConversionBundle();

    private ScalaConversionBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
