package org.jetbrains.plugins.scala.worksheet;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class WorksheetBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.WorksheetBundle";

    private static final WorksheetBundle INSTANCE = new WorksheetBundle();

    private WorksheetBundle() {
        super(BUNDLE);
    }

    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}