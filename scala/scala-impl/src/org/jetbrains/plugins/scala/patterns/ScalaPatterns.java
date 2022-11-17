package org.jetbrains.plugins.scala.patterns;

import com.intellij.patterns.PsiJavaPatterns;
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral;

public class ScalaPatterns extends PsiJavaPatterns {
    public static ScalaElementPattern.Capture<ScStringLiteral> scalaLiteral() {
        return new ScalaElementPattern.Capture<>(ScStringLiteral.class);
    }
}