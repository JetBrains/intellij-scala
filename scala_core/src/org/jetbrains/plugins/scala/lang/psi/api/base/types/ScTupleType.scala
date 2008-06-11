package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScTupleTypeElement extends ScTypeElement {
  def typeList = findChildByClass(classOf[ScTypes])

  def components = typeList.types
}