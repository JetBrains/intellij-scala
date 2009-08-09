package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScValueDeclarationImpl
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScVariableDeclarationImpl
import api.statements.{ScValue, ScVariable}
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

class ScVariableDeclarationElementType extends ScVariableElementType[ScVariable]("variable declaration"){
  def createElement(node: ASTNode): PsiElement = new ScVariableDeclarationImpl(node)

  def createPsi(stub: ScVariableStub) = new ScVariableDeclarationImpl(stub)
}