package org.jetbrains.plugins.scala
package lang.dependency

import com.intellij.psi.PsiElement
import lang.psi.api.base.ScReferenceElement
import lang.psi.api.expr.ScExpression

/**
 * Pavel Fatin
 */

trait DependencyKind {
  def isSatisfiedIn(element: PsiElement): Boolean
}

object DependencyKind {
  case object Reference extends DependencyKind{
    def isSatisfiedIn(element: PsiElement) = element match {
      case reference: ScReferenceElement => reference.resolve() != null
      case _ => false
    }
  }

  case object Conversion extends DependencyKind{
    def isSatisfiedIn(element: PsiElement) = element match {
      case expression: ScExpression =>
        expression.getTypeAfterImplicitConversion().implicitFunction.nonEmpty
    }
  }
}