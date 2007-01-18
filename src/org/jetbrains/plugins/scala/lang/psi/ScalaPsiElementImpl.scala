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
      def inner (e : PsiElement) : PsiElement = if (e == null || predicate(e)) e else inner(e.getNextSibling())

      inner (getFirstChild())
    }

    def childSatisfyPredicateForElementType(predicate : IElementType => Boolean) : PsiElement = {
      def inner (e : PsiElement) : PsiElement = if (e == null || predicate (e.getNode().getElementType())) e else inner(e.getNextSibling())

      inner (getFirstChild())
    }

    def childSatisfyPredicateForASTNode(predicate : ASTNode => Boolean) : PsiElement = {
      def inner (e : PsiElement) : PsiElement = if (e == null || predicate (e.getNode)) e else inner(e.getNextSibling())

      inner (getFirstChild())
    }

    def siblingSatisfyPredicate(predicate : IElementType => Boolean) : PsiElement = {
      def inner (e : PsiElement) : PsiElement =
        if (e == null || predicate (e.getNode.getElementType)) e else inner(e.getNextSibling)

      inner (this)
    }

//    def childSatisfyPredicateOnNode(predicate : ASTNode => Boolean) : PsiElement = {
//      def inner (e : PsiElement) : PsiElement = if (e == null || predicate (e.getNode())) e else inner(e.getNextSibling())
//
//      inner (getFirstChild())
//    }

    def hasChild[T >: Null <: ScalaPsiElementImpl] : Boolean = {
      return getChild[T] != null
    }

    [Nullable]
    def getChild[T >: Null <: ScalaPsiElementImpl] : T = {
      def inner (e : PsiElement) : PsiElement = e match {
         case null => null
         case me : T => me
         case _ => inner (e.getNextSibling())
      }

      inner (getFirstChild ()).asInstanceOf[T]
   }

  override def replace(newElement : PsiElement) : PsiElement = {
    if (newElement == null) throw new NullPointerException ("newElement is null")

    val parent : ScalaPsiElementImpl = getParent().asInstanceOf[ScalaPsiElementImpl]
    if (parent == null) throw new NullPointerException ("parent not null")

    val parentNode = parent.getNode()
    if (parentNode == null) throw new NullPointerException ("parentNode is null")

    val myNode = this.getASTNode()
    if (myNode == null) throw new NullPointerException ("myNode not null")

    val newElementNode = newElement.asInstanceOf[ScalaPsiElementImpl].getASTNode()
    if (newElementNode == null) throw new NullPointerException ("newElementNode is null" + myNode + " parentNode " + parentNode + " parent " + parent + " newElement " + newElement + " newElementNode " + newElementNode)



    parentNode.replaceChild(myNode, newElementNode)
    newElement
  }

  def getASTNode() : ASTNode = node

//  override def getUserData[T >: Null <: java.lang.Object]( key : Key[T] ) : T = null
//
//  override def putUserData[T >: Null <: java.lang.Object]( key : Key[T] , value : T) : Unit = {}

//  override def getIcon(flags : Int) : Icon = null
//
  override def toString : String = "scala psi element"
}