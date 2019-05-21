package org.jetbrains.plugins.scala.patterns;

import com.intellij.patterns.PsiJavaPatterns;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral;

public class ScalaPatterns extends PsiJavaPatterns {
    public static ScalaElementPattern.Capture<ScLiteral> scalaLiteral() {
        return new ScalaElementPattern.Capture<>(ScLiteral.class);
    }

    public static ScalaElementPattern.Capture<ScInterpolatedStringLiteral> interpolatedScalaLiteral() {
        return new ScalaElementPattern.Capture<>(ScInterpolatedStringLiteral.class);
    }
}