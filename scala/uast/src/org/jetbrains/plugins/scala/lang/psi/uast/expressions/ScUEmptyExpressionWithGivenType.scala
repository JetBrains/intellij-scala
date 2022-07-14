package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import com.intellij.psi.PsiType
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.ScUAnnotated
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast.UastEmptyExpression

/**
  * Mock for some unsupported expressions which at least have some type information
  *
  * @param getExpressionType specified type of this expression
  */
final class ScUEmptyExpressionWithGivenType(
  @Nullable override val getExpressionType: PsiType,
  parentProvider: LazyUElement
) extends UastEmptyExpression(parentProvider.force)
    with ScUAnnotated {

  def this(scExpression: ScExpression, parentProvider: LazyUElement) =
    this(scExpression.`type`().mapToOption(_.toPsiType).getOrElse(null), parentProvider)

  override def asRenderString: String = asLogString()
  override def asLogString: String =
    s"UastEmptyExpression(type = $getExpressionType)"
}
