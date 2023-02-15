package org.jetbrains.jps.incremental.scala;

import com.intellij.AbstractBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ResourceBundle;

public final class CompileServerBundle extends AbstractBundle {
    @NonNls
    private static final String BUNDLE = "messages.ScalaCompileServerBundle";

    private static final CompileServerBundle INSTANCE = new CompileServerBundle();

    private CompileServerBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }

    /**
     * The following code was copied from {@link org.jetbrains.jps.api.JpsDynamicBundle}, to avoid linking against it.
     */

    private static final String LANGUAGE_BUNDLE = "jps.language.bundle";

    private static final Logger LOG = Logger.getInstance(CompileServerBundle.class);

    private static final Method SET_PARENT = getSetParentMethod();
    private static final ClassLoader ourLangBundleLoader;
    static {
        ClassLoader loader = null;
        try {
            final String bundlePath = System.getProperty(LANGUAGE_BUNDLE, null);
            if (bundlePath != null) {
                loader = new URLClassLoader(new URL[] {new File(bundlePath).toURI().toURL()}, null);
            }
        }
        catch (Throwable e) {
            LOG.info(e);
        }
        finally {
            ourLangBundleLoader = loader;
        }
    }
    private static Method getSetParentMethod() {
        try {
            return ReflectionUtil.getDeclaredMethod(ResourceBundle.class, "setParent", ResourceBundle.class);
        }
        catch (Throwable e) {
            return null;
        }
    }

    /**
     * A base class for resource bundles used in JPS build process
     * This class provides support for "dynamic" resource bundles provided by {@link com.intellij.DynamicBundle.LanguageBundleEP} extension.
     * if JPS plugin inherits the ResourceBundle from this class, IDE's language pack's localized resources will be automatically available for the JPS process launched by the IDE.
     */

    @Override
    protected @NotNull ResourceBundle findBundle(
            @NotNull @NonNls String pathToBundle,
            @NotNull ClassLoader loader,
            @NotNull ResourceBundle.Control control
    ) {
        final ResourceBundle base = super.findBundle(pathToBundle, loader, control);
        final ClassLoader languageBundleLoader = ourLangBundleLoader;
        if (languageBundleLoader != null) {
            ResourceBundle languageBundle = super.findBundle(pathToBundle, languageBundleLoader, control);
            try {
                if (SET_PARENT != null) {
                    SET_PARENT.invoke(languageBundle, base);
                }
                return languageBundle;
            }
            catch (Throwable e) {
                LOG.warn(e);
            }
        }

        return base;
    }
}
