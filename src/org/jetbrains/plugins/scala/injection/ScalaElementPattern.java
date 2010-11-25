package org.jetbrains.plugins.scala.injection;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.ResolveResult;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScArgumentExprList;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall;
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScArguments;

/**
 * Pavel Fatin
 */

public class ScalaElementPattern<T extends ScalaPsiElement, Self extends ScalaElementPattern<T, Self>> extends PsiJavaElementPattern<T, Self> {
  public ScalaElementPattern(Class<T> tClass) {
    super(tClass);
  }

  public ScalaElementPattern(@NotNull InitialPatternCondition<T> tInitialPatternCondition) {
    super(tInitialPatternCondition);
  }

  public Self callArgument(final int index, final ElementPattern<? extends PsiMethod> methodPattern) {
    return with(new PatternCondition<T>("callArgument") {
      public boolean accepts(@NotNull final T literal, final ProcessingContext context) {
        final PsiElement parent = literal.getParent();
        if (parent instanceof ScArguments) {
          final ScArgumentExprList psiExpressionList = (ScArgumentExprList) parent;
          final ScExpression[] psiExpressions = psiExpressionList.exprsArray();
          if (!(psiExpressions.length > index && psiExpressions[index] == literal)) return false;

          final PsiElement element = psiExpressionList.getParent();
          if (element instanceof ScMethodCall) {
            final ScalaPsiElement expression = ((ScMethodCall) element).getInvokedExpr();
            if (expression instanceof ScReferenceElement) {
              final ScReferenceElement ref = (ScReferenceElement) expression;
              for (ResolveResult result : ref.multiResolve(false)) {
                final PsiElement psiElement = result.getElement();
                if (methodPattern.getCondition().accepts(psiElement, context)) {
                  return true;
                }
              }
            }
          }
        }
        return false;
      }
    });
  }

  public static class Capture<T extends ScalaPsiElement> extends ScalaElementPattern<T, Capture<T>> {
    public Capture(final Class<T> aClass) {
      super(aClass);
    }

    public Capture(@NotNull final InitialPatternCondition<T> condition) {
      super(condition);
    }
  }
}