package org.jetbrains.plugins.scala;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

import com.intellij.CommonBundle;

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.10.2006
 * Time: 11:14:45
 */
public class ScalaBundle {

    private static Reference<ResourceBundle> ourBundle;

    @NonNls
    private static final String BUNDLE = "org.jetbrains.plugins.scala.ScalaBundle";

    private ScalaBundle() {
    }

    public static String message(@PropertyKey(resourceBundle = BUNDLE)String key, Object... params) {
        return CommonBundle.message(getBundle(), key, params);
    }

    private static ResourceBundle getBundle() {
        ResourceBundle bundle = null;

        if (ourBundle != null) bundle = ourBundle.get();

        if (bundle == null) {
            bundle = ResourceBundle.getBundle(BUNDLE);
            ourBundle = new SoftReference<ResourceBundle>(bundle);
        }
        return bundle;
    }
}
