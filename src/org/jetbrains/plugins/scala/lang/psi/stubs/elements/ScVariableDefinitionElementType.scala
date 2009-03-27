package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScPatternDefinitionImpl
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScVariableDefinitionImpl
import api.statements.{ScVariable}
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

class ScVariableDefinitionElementType extends ScVariableElementType[ScVariable]("variable definition"){
  def createElement(node: ASTNode): PsiElement = new ScVariableDefinitionImpl(node)

  def createPsi(stub: ScVariableStub) = new ScVariableDefinitionImpl(stub)
}