package org.jetbrains.mill.language;

import com.intellij.lang.Language;
import org.jetbrains.plugins.scala.ScalaLanguage;

public final class MillLanguage extends Language {

    public static final MillLanguage INSTANCE = new MillLanguage();

    private MillLanguage() {
        super(ScalaLanguage.INSTANCE, "mill");
    }
}
