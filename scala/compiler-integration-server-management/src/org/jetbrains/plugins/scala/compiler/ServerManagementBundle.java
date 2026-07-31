package org.jetbrains.plugins.scala.compiler;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ServerManagementBundle{
    @NonNls
    private static final String BUNDLE = "messages.ServerManagementBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(ServerManagementBundle.class, BUNDLE);

    private ServerManagementBundle() {
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
