package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDeclarationImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 14.10.2008
  */
class ScFunctionDeclarationElementType extends ScFunctionElementType("function declaration") {
  override def createElement(node: ASTNode): ScFunctionDeclaration = new ScFunctionDeclarationImpl(node)

  override def createPsi(stub: ScFunctionStub): ScFunctionDeclaration = new ScFunctionDeclarationImpl(stub)
}