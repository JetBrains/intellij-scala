package org.jetbrains.plugins.scala.structureView;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class ScalaStructureViewBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaStructureViewBundle";

    private static final ScalaStructureViewBundle INSTANCE = new ScalaStructureViewBundle();

    private ScalaStructureViewBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
