package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScNewTemplateDefinitionImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.12.2009
 */
final class ScNewTemplateDefinitionStubElementType extends ScTemplateDefinitionElementType[ScNewTemplateDefinition]("new template definition stub") {

  override def createElement(node: ASTNode) = new ScNewTemplateDefinitionImpl(node)

  override def createPsi(stub: ScTemplateDefinitionStub) = new ScNewTemplateDefinitionImpl(stub)
}