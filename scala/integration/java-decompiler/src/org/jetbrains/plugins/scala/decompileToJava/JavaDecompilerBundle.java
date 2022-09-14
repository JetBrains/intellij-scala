package org.jetbrains.plugins.scala.decompileToJava;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class JavaDecompilerBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaJavaDecompilerBundle";

    private static final JavaDecompilerBundle INSTANCE = new JavaDecompilerBundle();

    private JavaDecompilerBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
