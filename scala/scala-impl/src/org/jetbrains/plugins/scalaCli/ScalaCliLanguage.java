package org.jetbrains.plugins.scalaCli;

import com.intellij.lang.DependentLanguage;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaLanguage;

public class ScalaCliLanguage extends Language implements DependentLanguage {

    @NotNull
    public static final ScalaCliLanguage INSTANCE = new ScalaCliLanguage();

    private ScalaCliLanguage() {
        super(ScalaLanguage.INSTANCE, "ScalaCli");
    }
}
