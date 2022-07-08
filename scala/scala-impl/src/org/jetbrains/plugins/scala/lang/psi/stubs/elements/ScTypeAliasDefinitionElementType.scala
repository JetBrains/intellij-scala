package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDefinitionImpl

class ScTypeAliasDefinitionElementType extends ScTypeAliasElementType("type alias definition"){
  override def createElement(node: ASTNode): ScTypeAliasDefinition = new ScTypeAliasDefinitionImpl(node)

  override def createPsi(stub: ScTypeAliasStub): ScTypeAliasDefinition = new ScTypeAliasDefinitionImpl(stub)
}