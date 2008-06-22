package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.extapi.psi.ASTWrapperPsiElement

/**
  @author ven
*/
class ScalaPsiElementImpl(node: ASTNode) extends ASTWrapperPsiElement(node)
        with ScalaPsiElement {

  // todo override in more specific cases
  override def replace(newElement: PsiElement): PsiElement = {
    getParent.getNode.replaceChild(node, newElement.getNode)
    newElement
  }

  override def toString = "scala psi element"
}