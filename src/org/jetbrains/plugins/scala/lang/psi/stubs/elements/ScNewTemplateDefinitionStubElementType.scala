package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScNewTemplateDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.12.2009
 */
class ScNewTemplateDefinitionStubElementType extends ScTemplateDefinitionElementType[ScNewTemplateDefinition]("new template definition stub") {
  override def createElement(node: ASTNode): ScTemplateDefinition = new ScNewTemplateDefinitionImpl(node)

  override def createPsi(stub: ScTemplateDefinitionStub): ScTemplateDefinition = new ScNewTemplateDefinitionImpl(stub)
}