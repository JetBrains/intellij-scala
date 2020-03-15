package org.jetbrains.jps.incremental.scala;


import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.10.2006
 *
 */
public class JpsBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaJpsBundle";

    private static final JpsBundle INSTANCE = new JpsBundle();

    private JpsBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}