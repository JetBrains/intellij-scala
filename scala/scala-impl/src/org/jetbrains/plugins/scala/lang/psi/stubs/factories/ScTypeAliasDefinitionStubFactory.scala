package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeAliasStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTypeAliasDefinitionElementType

final class ScTypeAliasDefinitionStubFactory(elementType: ScTypeAliasDefinitionElementType) extends ScTypeAliasStubFactory(elementType) {
  override def createPsi(stub: ScTypeAliasStub): ScTypeAliasDefinition = new ScTypeAliasDefinitionImpl(stub)
}
