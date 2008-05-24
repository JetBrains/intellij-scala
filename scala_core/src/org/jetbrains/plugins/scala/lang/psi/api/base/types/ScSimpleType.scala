package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScSimpleTypeElement extends ScTypeElement {

  def reference: ScReferenceElement = findChildByClass(classOf[ScReferenceElement])

  def singleton: Boolean

}