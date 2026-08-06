package org.jetbrains.plugins.scala.structureView;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class ScalaStructureViewBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaStructureViewBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(ScalaStructureViewBundle.class, BUNDLE);

    private ScalaStructureViewBundle() {
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
