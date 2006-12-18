package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.openapi.util.Key

import javax.swing.Icon

class ScalaPsiElementImpl( node : ASTNode ) extends ASTWrapperPsiElement( node )
  with ScalaPsiElement {

    def getChildNumber[T <: ScalaPsiElementImpl] : Int = {
      def inner (e : PsiElement, num : Int) : Int = e match {
         case null => -1
         case me : T => num
         case _ => inner (e.getNextSibling (), num + 1)
      }

      inner (getFirstChild (), 0)
    }

    def hasChild[T <: ScalaPsiElementImpl] : Boolean = {
      return getChild[T] != null
    }

    //nullable
    def getChild[T <: ScalaPsiElementImpl] : T = {
      def inner (e : PsiElement) : PsiElement = e match {
         case null => null
         case me : T => me
         case _ => inner (e.getNextSibling())
      }

      inner (getFirstChild ()).asInstanceOf[T]
   }

  def getASTNode() : ASTNode = node

  override def getUserData[T >: Null <: java.lang.Object]( key : Key[T] ) : T = null.asInstanceOf[T]

  override def putUserData[T >: Null <: java.lang.Object]( key : Key[T] , value : T) : Unit = {}

  override def getIcon(flags : Int) : Icon = null

  override def toString : String = null
}