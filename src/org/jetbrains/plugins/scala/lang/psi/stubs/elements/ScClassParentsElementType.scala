package org.jetbrains.plugins.scala.lang.psi.stubs.elements


import api.base.ScConstructor
import api.toplevel.templates.{ScTemplateParents, ScClassParents}
import psi.impl.toplevel.templates.ScClassParentsImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScClassParentsElementType extends ScTemplateParentsElementType[ScClassParents]("class parents") {
  def createPsi(stub: ScTemplateParentsStub): ScTemplateParents = new ScClassParentsImpl(stub)
}