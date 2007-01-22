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
class ScalaPsiElementImpl ( node : ASTNode ) extends ASTWrapperPsiElement( node )
  with ScalaPsiElement {

    def childrenOfType[T >: Null <: ScalaPsiElementImpl] (tokSet : TokenSet) : Iterable[T] = new Iterable[T] () {
     def elements = new Iterator[T] () {
        private def findChild (child : ASTNode) : ASTNode = child match {
           case null => null
           case _ => if (tokSet.contains(child.getElementType())) child else findChild (child.getTreeNext)
        }

        var n : ASTNode = findChild (getNode.getFirstChildNode)

        def hasNext = n != null

        def next : T =  if (n == null) null else {
          val res = n
          n = findChild (n.getTreeNext)
          res.getPsi().asInstanceOf[T]
        }
      }
    }

    def childSatisfyPredicateForPsiElement(predicate : PsiElement => Boolean) : PsiElement = {
      def inner(e : PsiElement) : PsiElement = if (e == null || predicate(e)) e else inner(e.getNextSibling)

      inner(getFirstChild)
    }

    def childSatisfyPredicateForASTNode(predicate : ASTNode => Boolean) : PsiElement = {
      def inner(e : PsiElement) : PsiElement = if (e == null || predicate(e.getNode)) e else inner(e.getNextSibling)

      inner(getFirstChild)
    }

    def childSatisfyPredicateForElementType(predicate : IElementType => Boolean) : PsiElement = {
      def inner(e : PsiElement) : PsiElement = if (e == null || predicate (e.getNode.getElementType)) e else inner(e.getNextSibling)

      inner(getFirstChild)
    }


    def hasChild[T >: Null <: ScalaPsiElementImpl] : Boolean = {
      return getChild[T] != null
    }

    def getChild[T >: Null <: ScalaPsiElementImpl] : T = {
      getChild[T](getFirstChild, (e : PsiElement) => e.getNextSibling)
    }

    def getChild[T >: Null <: ScalaPsiElementImpl](startsWith : PsiElement) : T = {
      getChild[T](startsWith, (e : PsiElement) => e.getNextSibling)
    }

    [Nullable]
    def getChild[T >: Null <: ScalaPsiElementImpl](startsWith : PsiElement, direction : PsiElement => PsiElement) : T = {
      def inner (e : PsiElement) : PsiElement = e match {
         case null => null
         case me : T => me
         case _ => inner (direction (e))
      }

      inner (startsWith).asInstanceOf[T]
   }

  override def replace(newElement : PsiElement) : PsiElement = {
    val parent : ScalaPsiElementImpl = getParent().asInstanceOf[ScalaPsiElementImpl]
    val parentNode = parent.getNode()
    val myNode = this.getASTNode()
    val newElementNode = newElement.asInstanceOf[ScalaPsiElementImpl].getASTNode()

    parentNode.replaceChild(myNode, newElementNode)
    newElement
  }

  def getASTNode() : ASTNode = node

  override def toString : String = "scala psi element"
}