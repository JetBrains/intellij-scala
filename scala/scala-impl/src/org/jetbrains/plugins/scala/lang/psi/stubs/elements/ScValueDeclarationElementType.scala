package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScValueDeclarationImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.10.2008
  */
class ScValueDeclarationElementType extends ScValueElementType[ScValueDeclaration]("value declaration") {
  override def createElement(node: ASTNode): ScValueDeclaration = new ScValueDeclarationImpl(node)

  override def createPsi(stub: ScValueStub): ScValueDeclaration = new ScValueDeclarationImpl(stub)
}