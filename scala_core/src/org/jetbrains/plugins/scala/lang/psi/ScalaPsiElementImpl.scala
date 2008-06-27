package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
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

  def findLastChildByType(t : IElementType) = {
    var node = getNode.getLastChildNode
    while(node != null && node.getElementType != t) {
      node = node.getTreePrev
    }
    if (node == null) null else node.getPsi
  }
}