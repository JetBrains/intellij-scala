package org.jetbrains.plugins.scala
package lang
package psi
package uast
package baseAdapters

import com.intellij.psi.{PsiElement, PsiType}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.uast.UExpression

/**
  * Scala adapter of the [[UExpression]].
  * Provides:
  *  - default implementations based on `scExpression`
  *
  * @note Just handy util - it is not obligatory to be mixed in by according ScU*** elements.
  * @example inherited by ScU*** elements in [[expressions]]
  */
trait ScUExpression extends ScUElement with UExpression {
  protected def scExpression: ScExpression

  override type PsiFacade = PsiElement
  override protected val scElement: PsiFacade = scExpression

  @Nullable
  override def getExpressionType: PsiType =
    scExpression.`type`().mapToOption(_.toPsiType).getOrElse(null)
}
