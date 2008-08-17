package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScSelfTypeElement extends ScalaPsiElement {
  def typeElement = findChild(classOf[ScTypeElement])
}