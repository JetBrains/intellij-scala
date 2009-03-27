package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScEnumerator extends ScalaPsiElement {
  
  def pattern: ScPattern

  def rvalue: ScExpression

}