package org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates

import psi.ScalaPsiElement
import base.types.ScTypeElement

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:23:53
*/

trait ScTemplateParents extends ScalaPsiElement {
  def typeElements() : Seq[ScTypeElement] = findChildrenByClass(classOf[ScTypeElement])
}