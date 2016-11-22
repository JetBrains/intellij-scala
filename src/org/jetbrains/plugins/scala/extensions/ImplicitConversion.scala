package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * Pavel Fatin
 */
object ImplicitConversion {
  def unapply(e: ScExpression): Option[PsiNamedElement] =
    e.getImplicitConversions(fromUnderscore = true)._2
}