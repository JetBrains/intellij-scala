package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScMacroDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScMacroDefinitionImpl

/**
 * User: Jason Zaugg
 */
class ScMacroDefinitionElementType extends ScFunctionElementType[ScMacroDefinition]("macro definition") {
  def createElement(node: ASTNode): PsiElement = new ScMacroDefinitionImpl(node)

  def createPsi(stub: ScFunctionStub) = new ScMacroDefinitionImpl(stub)
}