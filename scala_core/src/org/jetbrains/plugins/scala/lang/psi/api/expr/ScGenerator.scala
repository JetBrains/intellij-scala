package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern


/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScGenerator extends ScalaPsiElement {

  def pattern: ScPattern

  def guard: ScGuard

  def rvalue: ScExpression

}