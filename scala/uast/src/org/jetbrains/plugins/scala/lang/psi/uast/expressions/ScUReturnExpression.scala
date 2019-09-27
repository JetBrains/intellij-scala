package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReturn
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{
  ScUAnnotated,
  ScUElement,
  ScUExpression
}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast.{
  UExpression,
  UReturnExpression,
  UReturnExpressionAdapter
}

/**
  * [[ScReturn]] adapter for the [[UReturnExpression]]
  *
  * @param scExpression Scala PSI element representing `return` expression
  */
final class ScUReturnExpression(override protected val scExpression: ScReturn,
                                override protected val parent: LazyUElement)
    extends UReturnExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  @Nullable
  override def getReturnExpression: UExpression =
    scExpression.expr.map(_.convertToUExpressionOrEmpty(this)).orNull
}

// See KT-23557
/**
  * Implicit return for result statements in blocks.
  */
final class ScUImplicitReturnExpression(
  returnedElement: PsiElement,
  override protected val parent: LazyUElement,
  convertLambdas: Boolean = true
) extends UReturnExpressionAdapter
    with ScUElement
    with UExpression
    with ScUAnnotated {

  override type PsiFacade = PsiElement
  @Nullable
  override protected val scElement: PsiFacade = null

  @Nullable
  override lazy val getReturnExpression: UExpression =
    returnedElement.convertToUExpressionOrEmpty(parent = this, convertLambdas)

  /** equals & hashcode based on the [[returnedElement]] */
  override def canEqual(other: Any): Boolean =
    other.isInstanceOf[ScUImplicitReturnExpression]
  override def equals(other: Any): Boolean = other match {
    case that: ScUImplicitReturnExpression =>
      super.equals(that) &&
        (that canEqual this) &&
        getReturnExpression == that.getReturnExpression
    case _ => false
  }
  override def hashCode(): Int = {
    val state = Seq(super.hashCode(), getReturnExpression)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

object ScUImplicitReturnExpression {

  def convertAndWrapIntoReturn(
    psiElement: PsiElement,
    parent: LazyUElement,
    convertLambdas: Boolean = true
  ): UReturnExpression =
    new ScUImplicitReturnExpression(psiElement, parent, convertLambdas)
}
