package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.openapi.util.Key

import javax.swing.Icon

class ScalaPsiElementImpl( node : ASTNode ) extends ASTWrapperPsiElement( node )
  with ScalaPsiElement {

    def getChildNumber[T] : Int = {
      val children = getChildren

      for (val i <- Iterator.range(0, children.length); children(i).isInstanceOf[T]) {
        return i
      }
      return -1
    }

    def hasChild[T] : Boolean = {
      return getChild[T] != -1
    }

    //nullable
    def getChild[T] : T = {
      val num = getChildNumber[T]

      if (num != -1) getChildren.apply(num).asInstanceOf[T]
      else null
    }

  def getASTNode() : ASTNode = {
    //val key : Key[Int] = new Key("foo")
    node
  }

  override def getUserData[T <: java.lang.Object]( key : Key[T] ) : T = {
    return null;
  }

  override def putUserData[T <: java.lang.Object]( key : Key[T] , value : T) : Unit = {
  }

  override def getIcon(flags : Int) : Icon = {
    return null;
  }

  override def toString() : String = {
    return null;
  }

}