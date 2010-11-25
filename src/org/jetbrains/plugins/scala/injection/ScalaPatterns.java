package org.jetbrains.plugins.scala.injection;

import com.intellij.patterns.PsiJavaPatterns;
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement;

/**
 * Pavel Fatin
 */

public class ScalaPatterns extends PsiJavaPatterns {
  public static ScalaElementPattern scalaElement() {
    return new ScalaElementPattern.Capture<ScalaPsiElement>(ScalaPsiElement.class);
  }
}
