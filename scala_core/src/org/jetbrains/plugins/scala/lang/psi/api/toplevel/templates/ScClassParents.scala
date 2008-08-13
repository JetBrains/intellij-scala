package org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates
import base.ScParentConstructor

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:24:03
*/

trait ScClassParents extends ScTemplateParents {
  def constructor() = findChild(classOf[ScParentConstructor])
}