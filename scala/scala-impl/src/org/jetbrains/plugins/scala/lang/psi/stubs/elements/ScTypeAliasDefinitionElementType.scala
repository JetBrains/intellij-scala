package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeAliasStub

final class ScTypeAliasDefinitionElementType extends ScTypeAliasElementType("type alias definition") {
  override def createElement(node: ASTNode): ScTypeAliasDefinition = new ScTypeAliasDefinitionImpl(node)
}

final class ScTypeAliasDefinitionStubFactory(elementType: ScTypeAliasDefinitionElementType) extends ScTypeAliasStubFactory(elementType) {
  override def createPsi(stub: ScTypeAliasStub): ScTypeAliasDefinition = new ScTypeAliasDefinitionImpl(stub)
}
