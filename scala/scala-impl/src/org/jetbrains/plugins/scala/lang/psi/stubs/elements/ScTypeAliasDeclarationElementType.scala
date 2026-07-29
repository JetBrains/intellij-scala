package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDeclaration}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDeclarationImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeAliasStub

class ScTypeAliasDeclarationElementType extends ScTypeAliasElementType[ScTypeAlias](ScTypeAliasDeclarationElementType.DebugName) {
  override def createElement(node: ASTNode): ScTypeAliasDeclaration = new ScTypeAliasDeclarationImpl(node)
}

object ScTypeAliasDeclarationElementType {
  val DebugName = "type alias declaration"
}

class ScTypeAliasDeclarationStubFactory(elementType: IElementType)
  extends ScTypeAliasStubFactory(elementType, s"scala.${ScTypeAliasDeclarationElementType.DebugName}") {

  override def createPsi(stub: ScTypeAliasStub): ScTypeAliasDeclaration = new ScTypeAliasDeclarationImpl(stub)
}
