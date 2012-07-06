package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.psi.{PsiLiteral, PsiLanguageInjectionHost}
import psi.types.ScType

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
}

object ScLiteral {
  def unapply(literal: ScLiteral) = Some(literal.getValue)
}