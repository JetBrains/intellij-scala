package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, Nothing}

/** 
* @author Alexander Podkhalyuzin
* Date: 14.03.2008
*/

trait ScExpression extends ScalaPsiElement {
  def getType() : ScType = Nothing //todo
}