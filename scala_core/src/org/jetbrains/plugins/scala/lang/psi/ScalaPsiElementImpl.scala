package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.openapi.util.Key
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable

import javax.swing.Icon

/**
  @author ven
*/
class ScalaPsiElementImpl(node: ASTNode) extends ASTWrapperPsiElement(node)
        with ScalaPsiElement {

  // todo override in more specific cases
  override def replace(newElement: PsiElement): PsiElement = {
    newElement match {
      case ne: ScalaPsiElementImpl =>
        val parent: ScalaPsiElementImpl = getParent().asInstanceOf[ScalaPsiElementImpl]
        val parentNode = parent.getNode()
        val myNode = this.getASTNode()
        val newElementNode = ne.getASTNode()
        parentNode.replaceChild(myNode, newElementNode)
        ne
      case _ => null
    }
  }

  def getASTNode(): ASTNode = node

  override def findChild[T >: Null <: ScalaPsiElement](clazz: Class[T]): Option[T] = findChildByClass(clazz) match {
    case null => None
    case e => Some(e)
  }

  override def toString: String = "scala psi element"
}