package org.jetbrains.plugins.dotty;

import com.intellij.lang.Language;
import org.jetbrains.plugins.scala.ScalaLanguage;

/**
 * @author adkozlov
 */
public class DottyLanguage extends Language {

    public static final DottyLanguage INSTANCE = new DottyLanguage();

    private DottyLanguage() {
        super(ScalaLanguage.INSTANCE, "Dotty");
    }
}
