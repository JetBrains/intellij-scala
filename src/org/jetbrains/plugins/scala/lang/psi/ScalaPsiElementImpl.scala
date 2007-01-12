package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.openapi.util.Key
import com.intellij.psi.tree.TokenSet

import javax.swing.Icon

/**
  @author ven
*/
class ScalaPsiElementImpl( node : ASTNode ) extends ASTWrapperPsiElement( node )
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

    def childSatisfyPredicate(predicate : PsiElement => Boolean) : PsiElement = {
      def inner (e : PsiElement) : PsiElement = if (e == null || predicate (e)) e else inner(e.getNextSibling())

      inner (getFirstChild ())
    }
    
    def hasChild[T >: Null <: ScalaPsiElementImpl] : Boolean = {
      return getChild[T] != null
    }

    //nullable
    def getChild[T >: Null <: ScalaPsiElementImpl] : T = {
      def inner (e : PsiElement) : PsiElement = e match {
         case null => null
         case me : T => me
         case _ => inner (e.getNextSibling())
      }

      inner (getFirstChild ()).asInstanceOf[T]
   }

  def getASTNode() : ASTNode = node

  override def getUserData[T >: Null <: java.lang.Object]( key : Key[T] ) : T = null

  override def putUserData[T >: Null <: java.lang.Object]( key : Key[T] , value : T) : Unit = {}

  override def getIcon(flags : Int) : Icon = null

  override def toString : String = null
}