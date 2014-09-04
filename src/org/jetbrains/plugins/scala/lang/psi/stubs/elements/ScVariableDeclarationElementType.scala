package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScVariableDeclarationImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

class ScVariableDeclarationElementType extends ScVariableElementType[ScVariable]("variable declaration"){
  def createElement(node: ASTNode): PsiElement = new ScVariableDeclarationImpl(node)

  def createPsi(stub: ScVariableStub) = new ScVariableDeclarationImpl(stub)
}