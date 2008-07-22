package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl


import com.intellij.lang.ASTNode
import _root_.org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import api.ScDocComment

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocCommentImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocComment{
  override def toString: String = "DocComment"
}