package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScClassParents, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScClassParentsImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScClassParentsElementType extends ScTemplateParentsElementType[ScClassParents]("class parents") {
  def createPsi(stub: ScTemplateParentsStub): ScTemplateParents = new ScClassParentsImpl(stub)

  override def isLeftBound = true
}