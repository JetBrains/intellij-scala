package org.jetbrains.plugins.scalaDoc;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaLanguage;

public class ScalaDocLanguage extends Language {

    @NotNull
    public static final ScalaDocLanguage INSTANCE = new ScalaDocLanguage();

    private ScalaDocLanguage() {
        super(ScalaLanguage.INSTANCE, "ScalaDoc");
    }
}
