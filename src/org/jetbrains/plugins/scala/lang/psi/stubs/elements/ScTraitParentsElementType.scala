package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import api.toplevel.templates.{ScTemplateParents, ScTraitParents}
import psi.impl.toplevel.templates.ScTraitParentsImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScTraitParentsElementType extends ScTemplateParentsElementType[ScTraitParents]("trait parents") {
  def createPsi(stub: ScTemplateParentsStub): ScTemplateParents = new ScTraitParentsImpl(stub)
}