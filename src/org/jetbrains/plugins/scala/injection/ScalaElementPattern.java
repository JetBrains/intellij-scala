package org.jetbrains.plugins.scala.injection;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement;
import org.jetbrains.plugins.scala.lang.psi.api.expr.*;
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

  public Self isRegExpLiteral() {
    return with(new PatternCondition<T>("isRegExpLiteral") {
      public boolean accepts(@NotNull final T literal, final ProcessingContext context) {
        final PsiElement parent = literal.getParent();
        if(parent instanceof ScReferenceExpression && parent.getText().endsWith(".r")) return true;
        if(parent instanceof ScPostfixExpr && ((ScPostfixExpr) parent).operation().getText().equals("r")) return true;
        return false;
      }
    });
  }

  public Self callTarget(final ElementPattern<? extends PsiMethod> methodPattern) {
    return with(new PatternCondition<T>("callTarget") {
      public boolean accepts(@NotNull final T host, final ProcessingContext context) {
        final PsiElement element = host.getParent();
        if (element instanceof ScReferenceExpression) {
          final ScReferenceExpression expression = (ScReferenceExpression) element;
          for (final ResolveResult result : expression.multiResolve(false))
            if (methodPattern.getCondition().accepts(result.getElement(), context))
              return true;
        }
        return false;
      }
    });
  }

  public Self callArgument(final int index, final ElementPattern<? extends PsiMethod> methodPattern) {
    return with(new PatternCondition<T>("callArgument") {
      public boolean accepts(@NotNull final T host, final ProcessingContext context) {
        final PsiElement parent = host.getParent();
        if (parent instanceof ScArguments) {
          final ScArgumentExprList psiExpressionList = (ScArgumentExprList) parent;
          final ScExpression[] psiExpressions = psiExpressionList.exprsArray();
          if (!(psiExpressions.length > index && psiExpressions[index] == host)) return false;

          final PsiElement element = psiExpressionList.getParent();
          if (element instanceof ScMethodCall) {
            final ScalaPsiElement expression = ((ScMethodCall) element).getInvokedExpr();
            if (expression instanceof ScReferenceElement) {
              final ScReferenceElement ref = (ScReferenceElement) expression;
              for (ResolveResult result : ref.multiResolve(false))
                if (methodPattern.getCondition().accepts(result.getElement(), context))
                  return true;
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