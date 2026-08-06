package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolated, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnnotated, ScUExpression}
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.{ULiteralExpression, ULiteralExpressionAdapter}

object ScULiteral {
  /**
   * [[ScLiteral]] adapter for the [[ULiteralExpression]]
   *
   * @param literal Scala PSI element representing literal expression
   */
  def apply(literal: ScLiteral, parent: LazyUElement): ULiteralExpression = literal match {
    case intrp: ScInterpolated if intrp.getInjections.nonEmpty => new ScULiteral(literal, parent)
    case str: ScStringLiteral                                  => new ScUStringLiteral(str, parent)
    case _                                                     => new ScULiteral(literal, parent)
  }

  private class ScULiteral(override protected val scExpression: ScLiteral,
                           override protected val parent: LazyUElement)
    extends ULiteralExpressionAdapter
      with ScUExpression
      with ScUAnnotated {

    @Nullable
    override def getValue: AnyRef = scExpression.getValue

    @Nullable
    override def evaluate(): AnyRef = getValue
  }

  private class ScUStringLiteral(override protected val scExpression: ScStringLiteral,
                                 override protected val parent: LazyUElement)
    extends ScULiteral(scExpression, parent) with UInjectionHost {

    override lazy val isString: Boolean = super[UInjectionHost].isString

    override def getPsiLanguageInjectionHost: PsiLanguageInjectionHost = scExpression

    override def evaluateToString(): String = scExpression.getValue
  }
}
