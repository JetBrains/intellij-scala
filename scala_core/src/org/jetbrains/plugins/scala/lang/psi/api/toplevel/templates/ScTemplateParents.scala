package org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates
import org.jetbrains.plugins.scala.lang.psi.api.base.{types, ScConstructor}
import types.ScTypeElement

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:24:03
*/

trait ScTemplateParents extends ScParents {
  def traits() : Seq[ScTypeElement] = findChildrenByClass(classOf[ScTypeElement])

  def constructor() = findChild(classOf[ScConstructor])
}