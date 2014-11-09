package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiAnnotationOwner, PsiElement, PsiLanguageInjectionHost, PsiLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScLiteral extends ScExpression with PsiLiteral with PsiLanguageInjectionHost {
  /**
   * This method works only for null literal (to avoid possibly dangerous usage)
   * @param tp type, which should be returned by method getTypeWithouImplicits
   */
  def setTypeWithoutImplicits(tp: Option[ScType])
  def isString: Boolean
  def isMultiLineString: Boolean
  def getAnnotationOwner(annotationOwnerLookUp: ScLiteral => Option[PsiAnnotationOwner with PsiElement]): Option[PsiAnnotationOwner]
  def isSymbol: Boolean
  def isChar: Boolean
  def contentRange: TextRange
}

object ScLiteral {
  def unapply(literal: ScLiteral) = Some(literal.getValue)
}