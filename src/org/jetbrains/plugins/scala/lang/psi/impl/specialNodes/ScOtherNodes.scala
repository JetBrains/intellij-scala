package org.jetbrains.plugins.scala.lang.psi.impl.specialNodes {

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.PsiElement

/**
 * User: Dmitry.Krasilschikov
 * Date: 07.11.2006
 * Time: 15:28:13
 */

  class ScTrash ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString = "trash"
  }

}