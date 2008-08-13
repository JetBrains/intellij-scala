package org.jetbrains.plugins.scala.lang.psi.api.expr

import base.ScParentConstructor
import statements.{ScConstructor}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScAnnotationExpr extends ScalaPsiElement {
  def constr = findChildByClass(classOf[ScParentConstructor])
}