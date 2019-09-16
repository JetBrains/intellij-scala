package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import com.intellij.psi.PsiType
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAssignment
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter._
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
  * [[ScAssignment]] adapter for the [[UNamedExpression]].
  * Represents expression with name (e.g. argument with specified name).
  *
  * Example: --------------v
  * ------------
  * {{{@MyAnnotation(name = value)}}}
  *
  * @param scExpression Scala PSI element representing named expression (e.g. named argument)
  * @see [[ScUUnnamedExpression]]
  */
class ScUNamedExpression(override protected val scExpression: ScAssignment,
                         override protected val parent: LazyUElement)
    extends UNamedExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  override def getExpression: UExpression =
    scExpression.rightExpression.convertToUExpressionOrEmpty(this)

  @Nullable
  override def getName: String = scExpression.referenceName.orNull

  @Nullable
  override def getExpressionType: PsiType = getExpression.getExpressionType
}
