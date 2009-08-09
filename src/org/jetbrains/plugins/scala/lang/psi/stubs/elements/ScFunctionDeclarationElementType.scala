package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDeclarationImpl
import api.statements.ScFunction
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.10.2008
 */

class ScFunctionDeclarationElementType extends ScFunctionElementType[ScFunction]("function declaration"){
  def createElement(node: ASTNode): PsiElement = new ScFunctionDeclarationImpl(node)

  def createPsi(stub: ScFunctionStub) = new ScFunctionDeclarationImpl(stub)
}