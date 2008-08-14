package org.jetbrains.plugins.scala.lang.psi.api.base

import psi.ScalaPsiElement
import statements.ScFunction
import types.ScTypeElement

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScConstructor extends ScalaPsiElement {
  def typeElement() = findChildByClass(classOf[ScTypeElement])
}