package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScSimpleTypeElement extends ScTypeElement {

  def reference = findChild(classOf[ScStableCodeReferenceElement])
  def pathElement = findChildByClass(classOf[ScPathElement])

  def singleton: Boolean

}