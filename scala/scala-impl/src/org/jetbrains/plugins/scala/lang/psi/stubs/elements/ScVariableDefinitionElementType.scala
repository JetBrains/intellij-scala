package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScVariableDefinitionImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 18.10.2008
  */
class ScVariableDefinitionElementType extends ScVariableElementType[ScVariableDefinition]("variable definition") {
  override def createElement(node: ASTNode): ScVariableDefinition = new ScVariableDefinitionImpl(node)

  override def createPsi(stub: ScVariableStub): ScVariableDefinition = new ScVariableDefinitionImpl(stub)
}