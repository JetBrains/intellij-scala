package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDeclaration}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDeclarationImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeAliasStub

class ScTypeAliasDeclarationElementType extends ScTypeAliasElementType[ScTypeAlias]("type alias declaration") {
  override def createElement(node: ASTNode): ScTypeAliasDeclaration = new ScTypeAliasDeclarationImpl(node)

  override def createPsi(stub: ScTypeAliasStub): ScTypeAliasDeclaration = new ScTypeAliasDeclarationImpl(stub)
}