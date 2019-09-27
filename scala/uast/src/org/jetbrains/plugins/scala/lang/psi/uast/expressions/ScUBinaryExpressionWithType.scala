package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import com.intellij.psi.PsiType
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTypedExpression
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{
  ScUAnnotated,
  ScUExpression
}
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast._

/**
  * [[ScTypedExpression]] adapter for the [[UBinaryExpressionWithType]]
  *
  * @param scExpression Scala PSI element representing typed expression (e.g. `42: Int`)
  */
final class ScUBinaryExpressionWithType(
  override protected val scExpression: ScTypedExpression,
  override protected val parent: LazyUElement
) extends UBinaryExpressionWithTypeAdapter
    with ScUExpression
    with ScUAnnotated {

  override def getOperand: UExpression =
    scExpression.expr.convertToUExpressionOrEmpty(this)

  override def getOperationKind: UastBinaryExpressionWithTypeKind =
    UastBinaryExpressionWithTypeKind.TYPE_CAST

  override def getType: PsiType = scExpression.uastType()

  @Nullable
  override def getTypeReference: UTypeReferenceExpression =
    scExpression.typeElement
      .flatMap(_.convertTo[UTypeReferenceExpression](this))
      .orNull
}
