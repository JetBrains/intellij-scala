package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

trait ScExistentialTypeElement extends ScTypeElement {
  def quantified = findChildByClass(classOf[ScTypeElement])
  def clause = findChildByClass(classOf[ScExistentialClause])
}