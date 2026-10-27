package org.jetbrains.plugins.scala;

import com.intellij.lang.DependentLanguage;
import com.intellij.lang.InjectableLanguage;
import com.intellij.lang.Language;
import com.intellij.lang.jvm.JvmLanguage;

public class Scala3Language extends Language implements JvmLanguage, DependentLanguage, InjectableLanguage {

    public static final Scala3Language INSTANCE = new Scala3Language();

    private Scala3Language() {
        super(ScalaLanguage.INSTANCE, "Scala 3");
    }
}
