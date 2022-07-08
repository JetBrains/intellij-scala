package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDeclaration}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDeclarationImpl

class ScTypeAliasDeclarationElementType extends ScTypeAliasElementType[ScTypeAlias]("type alias declaration") {
  override def createElement(node: ASTNode): ScTypeAliasDeclaration = new ScTypeAliasDeclarationImpl(node)

  override def createPsi(stub: ScTypeAliasStub): ScTypeAliasDeclaration = new ScTypeAliasDeclarationImpl(stub)
}