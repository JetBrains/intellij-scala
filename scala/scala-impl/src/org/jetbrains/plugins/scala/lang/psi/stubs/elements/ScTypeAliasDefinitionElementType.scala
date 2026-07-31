package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDefinitionImpl

final class ScTypeAliasDefinitionElementType extends ScTypeAliasElementType("type alias definition") {
  override def createElement(node: ASTNode): ScTypeAliasDefinition = new ScTypeAliasDefinitionImpl(node)
}
