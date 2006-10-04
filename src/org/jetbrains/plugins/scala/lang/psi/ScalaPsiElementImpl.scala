package org.jetbrains.plugins.scala.lang.psi
.impl

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.openapi.util.Key

import javax.swing.Icon

/**
 * User: Dmitry.Krasilschikov
 * Date: 03.10.2006
 * Time: 21:33:21
 */

class ScalaPsiElementImpl( node : ASTNode ) extends ASTWrapperPsiElement( node ) {
  def getASTNode() : ASTNode = {
    //val key : Key[Int] = new Key("foo")
    node
  }

  override def getUserData[T]( key : Key[T] ) : T = {
    return null;
  }

  override def putUserData[T]( key : Key[T] , value : T) : Unit = {
  }

  override def getIcon(flags : Int) : Icon = {
    return null;
  }

  override def toString() : String = {
    return null;
  }

}