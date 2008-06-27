package org.jetbrains.plugins.scala.lang.psi.api.expr.util

import com.intellij.psi.PsiManager
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition

/**
* User: Alexander Podkhalyuzin
* Date: 27.06.2008
*/

trait ScBlocker{
  def addDefinition(decl: PsiElement, before: PsiElement): Boolean = {
    if (!(decl.isInstanceOf[ScPatternDefinition] || decl.isInstanceOf[ScVariableDefinition])) {
      return false
    }
    getNode.addChild(decl.copy.getNode,before.getNode)
    getNode.addChild(ScalaPsiElementFactory.createNewLineNode(getManager), before.getNode)
    return true
  }
  def getNode: ASTNode
  def getManager: PsiManager
}