package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.openapi.util.Key

import javax.swing.Icon

class ScalaPsiElementImpl( node : ASTNode ) extends ASTWrapperPsiElement( node )
  with ScalaPsiElement {

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