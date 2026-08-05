package org.jetbrains.plugins.scalaDirective;

import com.intellij.lang.DependentLanguage;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaLanguage;

public class ScalaDirectiveLanguage extends Language implements DependentLanguage {

    @NotNull
    public static final ScalaDirectiveLanguage INSTANCE = new ScalaDirectiveLanguage();

    private ScalaDirectiveLanguage() {
        super(ScalaLanguage.INSTANCE, "ScalaDirective");
    }
}
