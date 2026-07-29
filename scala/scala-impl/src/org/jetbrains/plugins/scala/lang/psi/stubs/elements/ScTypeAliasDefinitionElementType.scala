package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeAliasStub

class ScTypeAliasDefinitionElementType extends ScTypeAliasElementType(ScTypeAliasDefinitionElementType.DebugName) {
  override def createElement(node: ASTNode): ScTypeAliasDefinition = new ScTypeAliasDefinitionImpl(node)
}

object ScTypeAliasDefinitionElementType {
  val DebugName = "type alias definition"
}

class ScTypeAliasDefinitionStubFactory(elementType: IElementType)
  extends ScTypeAliasStubFactory(elementType, s"scala.${ScTypeAliasDefinitionElementType.DebugName}") {

  override def createPsi(stub: ScTypeAliasStub): ScTypeAliasDefinition = new ScTypeAliasDefinitionImpl(stub)
}
