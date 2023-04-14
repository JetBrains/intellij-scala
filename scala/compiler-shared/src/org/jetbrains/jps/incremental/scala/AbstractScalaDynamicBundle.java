package org.jetbrains.jps.incremental.scala;

import com.intellij.AbstractBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ResourceBundle;

@ApiStatus.Internal
public abstract class AbstractScalaDynamicBundle extends AbstractBundle {

    protected AbstractScalaDynamicBundle(@NonNls @NotNull String pathToBundle) {
        super(pathToBundle);
    }

    /**
     * The following code was copied from org.jetbrains.jps.api.JpsDynamicBundle, to avoid linking against it.
     */

    private static final String LANGUAGE_BUNDLE = "jps.language.bundle";

    private static final Logger LOG = Logger.getInstance(AbstractScalaDynamicBundle.class);

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

    @SuppressWarnings("deprecation")
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
