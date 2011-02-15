package org.jetbrains.plugins.scala.injection;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.psi.*;
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

public class ScalaElementPattern<T extends ScLiteral, Self extends ScalaElementPattern<T, Self>> extends PsiJavaElementPattern<T, Self> {
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

  public Self callArgument(final int index, final String target, final String method, final String... parameters) {
    return with(new PatternCondition<T>("callArgument") {
      public boolean accepts(@NotNull final T host, final ProcessingContext context) {
        return isArgumentInCall(host, method, parameters.length, index) && // check without method resolving
            isArgumentForMethod(host, target, method, parameters); // check with method resolving
      }
    });
  }

  private static boolean isArgumentForMethod(PsiElement host, String target, String method, String... parameters) {
    PsiElement parent = host.getParent();
    if (parent instanceof ScArguments) {
      PsiElement element = parent.getParent();
      if (element instanceof ScMethodCall) {
        ScalaPsiElement expression = ((ScMethodCall) element).getInvokedExpr();
        if (expression instanceof ScReferenceElement) {
          for (ResolveResult result : ((ScReferenceElement) expression).multiResolve(false)) {
            PsiElement targetElement = result.getElement();
            if(targetElement instanceof PsiMethod) {
              PsiMethod targetMehtod = (PsiMethod) targetElement;
              if(!method.equals(targetMehtod.getName())) return false;
              PsiParameter[] methodParameters = targetMehtod.getParameterList().getParameters();
              if(methodParameters.length != parameters.length) return false;
              for(int i = 0; i < methodParameters.length; i++) {
                if(!methodParameters[i].getType().getCanonicalText().equals(parameters[i])) return false;
              }
              PsiClass targetClass = targetMehtod.getContainingClass();
              return targetClass != null && target.equals(targetClass.getQualifiedName());
            }
          }
        }
      }
    }
    return false;
  }

  private static boolean isArgumentInCall(PsiElement host, String method, int parameters, int index) {
    PsiElement parent = host.getParent();

    if (parent instanceof ScArguments) {
      ScArgumentExprList list = (ScArgumentExprList) parent;
      ScExpression[] arguments = list.exprsArray();

      if(arguments.length != parameters) return false;
      if (arguments[index] != host) return false;

      PsiElement prevSibling = list.getPrevSibling();
      if(prevSibling == null) return false;

      PsiElement lastChild = prevSibling.getLastChild();
      if(lastChild == null) return false;

      return method.equals(lastChild.getText());
    }

    return false;
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