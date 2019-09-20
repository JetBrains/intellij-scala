package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{
  ScUAnnotated,
  ScUExpression
}
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast.{
  UExpression,
  UNamedExpression,
  UNamedExpressionAdapter
}

/**
  * [[ScExpression]] adapter for the [[UNamedExpression]].
  * Represents expression which has name but it is omitted at the use site
  * (e.g. plain parameters without specified name).
  *
  * Example: ----------V
  * {{{@MyAnnotation(value)}}}
  *
  * @param scExpression Scala PSI element representing expression
  *                     which have name omitted (e.g. argument)
  * @see [[ScUNamedExpression]]
  */
class ScUUnnamedExpression(
  override protected val scExpression: ScExpression,
  override protected val parent: LazyUElement
) extends UNamedExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  override def getExpression: UExpression =
    scExpression.convertToUExpressionOrEmpty(this)

  @Nullable
  override def getSourcePsi: PsiElement = null

  @Nullable
  override def getName: String = null

  override def asLogString(): String = "UUnnamedExpression"
}
