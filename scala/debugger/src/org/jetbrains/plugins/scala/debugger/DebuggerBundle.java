package org.jetbrains.plugins.scala.debugger;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.plugins.scala.NlsString;

public final class DebuggerBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.DebuggerBundle";

    private static final DebuggerBundle INSTANCE = new DebuggerBundle();

    private DebuggerBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
