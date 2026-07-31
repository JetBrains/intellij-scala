package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDeclarationImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeAliasStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTypeAliasDeclarationElementType

final class ScTypeAliasDeclarationStubFactory(elementType: ScTypeAliasDeclarationElementType) extends ScTypeAliasStubFactory(elementType) {
  override def createPsi(stub: ScTypeAliasStub): ScTypeAliasDeclaration = new ScTypeAliasDeclarationImpl(stub)
}
