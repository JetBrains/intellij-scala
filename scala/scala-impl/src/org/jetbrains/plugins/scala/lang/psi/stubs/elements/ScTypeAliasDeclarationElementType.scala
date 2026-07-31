package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDeclarationImpl

final class ScTypeAliasDeclarationElementType extends ScTypeAliasElementType("type alias declaration") {
  override def createElement(node: ASTNode): ScTypeAliasDeclaration = new ScTypeAliasDeclarationImpl(node)
}
