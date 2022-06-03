package org.jetbrains.plugins.scala.patterns;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral;

public class ScalaElementPattern<T extends ScalaPsiElement, Self extends ScalaElementPattern<T, Self>> extends PsiJavaElementPattern<T, Self> {
  public ScalaElementPattern(Class<T> tClass) {
    super(tClass);
  }

  public ScalaElementPattern(@NotNull InitialPatternCondition<T> tInitialPatternCondition) {
    super(tInitialPatternCondition);
  }

  public Self isRegExpLiteral() {
    return with(new PatternCondition<T>("isRegExpLiteral") {
      public boolean accepts(@NotNull final T literal, final ProcessingContext context) {
        return ScalaElementPatternImpl.isRegExpLiteral(literal);
      }
    });
  }

  public Self callArgument(final int index, final ElementPattern<? extends PsiMethod> methodPattern) {
    return with(new PatternCondition<T>("callArgument") {
      public boolean accepts(@NotNull final T host, final ProcessingContext context) {
        return ScalaElementPatternImpl.isMethodCallArgument(host, context, index, methodPattern);
      }
    });
  }

  public static class Capture<T extends ScLiteral> extends ScalaElementPattern<T, Capture<T>> {
    public Capture(final Class<T> aClass) {
      super(aClass);
    }

    public Capture(@NotNull final InitialPatternCondition<T> condition) {
      super(condition);
    }
  }
}