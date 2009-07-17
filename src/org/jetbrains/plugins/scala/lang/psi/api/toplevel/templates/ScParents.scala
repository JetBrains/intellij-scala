package org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates

import impl.ScalaPsiElementFactory
import psi.ScalaPsiElement
import base.types.ScTypeElement
import types.ScType

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:23:53
*/

trait ScTemplateParents extends ScalaPsiElement {
  def typeElements: Seq[ScTypeElement] = Seq(findChildrenByClass(classOf[ScTypeElement]) :_*)
  def superTypes: Seq[ScType]
}