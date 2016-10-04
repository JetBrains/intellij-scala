package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScVariableDeclarationImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 18.10.2008
  */
class ScVariableDeclarationElementType extends ScVariableElementType[ScVariableDeclaration]("variable declaration") {
  override def createElement(node: ASTNode): ScVariableDeclaration = new ScVariableDeclarationImpl(node)

  override def createPsi(stub: ScVariableStub): ScVariableDeclaration = new ScVariableDeclarationImpl(stub)
}