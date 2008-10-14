package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl
import api.statements.ScFunction
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.10.2008
 */

class ScFunctionDefinitionElementType extends ScFunctionElementType[ScFunction]("function definition"){
  def createElement(node: ASTNode): PsiElement = new ScFunctionDefinitionImpl(node)

  def createPsi(stub: ScFunctionStub) = new ScFunctionDefinitionImpl(stub)
}