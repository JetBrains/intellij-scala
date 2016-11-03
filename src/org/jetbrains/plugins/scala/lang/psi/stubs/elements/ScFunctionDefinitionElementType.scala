package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 14.10.2008
  */
class ScFunctionDefinitionElementType extends ScFunctionElementType("function definition") {
  override def createElement(node: ASTNode): ScFunctionDefinition = new ScFunctionDefinitionImpl(node)

  override def createPsi(stub: ScFunctionStub): ScFunctionDefinition = new ScFunctionDefinitionImpl(stub)
}