package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import psi.impl.statements.ScMacroDefinitionImpl
import api.statements.ScMacroDefinition

/**
 * User: Jason Zaugg
 */
class ScMacroDefinitionElementType extends ScFunctionElementType[ScMacroDefinition]("macro definition") {
  def createElement(node: ASTNode): PsiElement = new ScMacroDefinitionImpl(node)

  def createPsi(stub: ScFunctionStub) = new ScMacroDefinitionImpl(stub)
}