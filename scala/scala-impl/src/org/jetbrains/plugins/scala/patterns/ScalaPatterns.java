package org.jetbrains.plugins.scala.patterns;

import com.intellij.patterns.PsiJavaPatterns;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral;

public class ScalaPatterns extends PsiJavaPatterns {
    public static ScalaElementPattern.Capture<ScLiteral> scalaLiteral() {
        return new ScalaElementPattern.Capture<>(ScLiteral.class);
    }
}