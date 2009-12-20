package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScNewTemplateDefinitionImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.12.2009
 */

class ScNewTemplateDefinitionStubElementType extends ScTemplateDefinitionElementType[ScNewTemplateDefinition]("new template definition stub") {
  def createPsi(stub: ScTemplateDefinitionStub): ScTemplateDefinition = new ScNewTemplateDefinitionImpl(stub)
}