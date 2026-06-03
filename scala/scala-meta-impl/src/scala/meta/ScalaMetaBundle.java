package scala.meta;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ScalaMetaBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaMetaBundle";

    private static final ScalaMetaBundle INSTANCE = new ScalaMetaBundle();

    private ScalaMetaBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
