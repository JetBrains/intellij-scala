package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDeclarationImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeAliasStub

final class ScTypeAliasDeclarationElementType extends ScTypeAliasElementType("type alias declaration") {
  override def createElement(node: ASTNode): ScTypeAliasDeclaration = new ScTypeAliasDeclarationImpl(node)
}

final class ScTypeAliasDeclarationStubFactory(elementType: ScTypeAliasDeclarationElementType) extends ScTypeAliasStubFactory(elementType) {
  override def createPsi(stub: ScTypeAliasStub): ScTypeAliasDeclaration = new ScTypeAliasDeclarationImpl(stub)
}
