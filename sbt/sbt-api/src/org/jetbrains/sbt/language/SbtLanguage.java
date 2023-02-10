package org.jetbrains.sbt.language;

import com.intellij.lang.Language;
import org.jetbrains.plugins.scala.ScalaLanguage;

public final class SbtLanguage extends Language {

    public static final SbtLanguage INSTANCE = new SbtLanguage();

    private SbtLanguage() {
        super(ScalaLanguage.INSTANCE, "sbt");
    }
}
