package org.jetbrains.plugins.scala.highlighter;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public class ScalaOptionsBundle extends DynamicBundle {
    @NonNls
    public static final String BUNDLE = "messages.ScalaOptionsBundle";
    public static final ScalaOptionsBundle INSTANCE = new ScalaOptionsBundle();

    private ScalaOptionsBundle() { super(BUNDLE); }

    @NotNull
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,  @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }

    @NotNull
    public static Supplier<String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,  @NotNull Object... params) {
        return INSTANCE.getLazyMessage(key, params);
    }
}