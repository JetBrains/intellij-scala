package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.10.2008
 */

class ScFunctionDefinitionElementType extends ScFunctionElementType[ScFunction]("function definition"){
  def createElement(node: ASTNode): PsiElement = new ScFunctionDefinitionImpl(node)

  def createPsi(stub: ScFunctionStub) = new ScFunctionDefinitionImpl(stub)
}