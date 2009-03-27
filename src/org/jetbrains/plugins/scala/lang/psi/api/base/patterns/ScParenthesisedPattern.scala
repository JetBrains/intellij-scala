package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScParenthesisedPattern extends ScPattern {
  def subpattern = findChildByClass(classOf[ScPattern]) // not null, otherwise it is a tuple pattern
  override def calcType = subpattern.calcType
}