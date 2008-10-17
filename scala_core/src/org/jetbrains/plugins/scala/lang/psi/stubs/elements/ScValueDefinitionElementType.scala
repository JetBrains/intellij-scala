package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScPatternDefinitionImpl
import api.statements.ScValue
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.10.2008
 */

class ScValueDefinitionElementType extends ScValueElementType[ScValue]("value definition"){
  def createElement(node: ASTNode): PsiElement = new ScPatternDefinitionImpl(node)

  def createPsi(stub: ScValueStub) = new ScPatternDefinitionImpl(stub)
}