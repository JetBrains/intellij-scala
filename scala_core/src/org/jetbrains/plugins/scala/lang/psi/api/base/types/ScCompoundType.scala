package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScCompoundTypeElement extends ScTypeElement {
  def components = findChildrenByClass(classOf[ScTypeElement])
  def refinement = findChild(classOf[ScRefinement])
}