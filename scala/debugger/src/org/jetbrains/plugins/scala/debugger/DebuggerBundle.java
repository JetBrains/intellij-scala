package org.jetbrains.plugins.scala.debugger;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class DebuggerBundle {
    @NonNls
    private static final String BUNDLE = "messages.DebuggerBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(DebuggerBundle.class, BUNDLE);

    private DebuggerBundle() {
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
