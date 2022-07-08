package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

object ImplicitConversion {
  def unapply(e: ScExpression): Option[PsiNamedElement] =
    e.implicitElement(fromUnderscore = true)
}