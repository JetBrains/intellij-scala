package org.jetbrains.plugins.scala;

import com.intellij.lang.DependentLanguage;
import com.intellij.lang.Language;

public class Scala3Language extends Language implements DependentLanguage {

    public static final Scala3Language INSTANCE = new Scala3Language();

    private Scala3Language() {
        super(ScalaLanguage.INSTANCE, "Scala 3");
    }
}
