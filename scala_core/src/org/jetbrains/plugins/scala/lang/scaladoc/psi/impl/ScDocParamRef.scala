package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl


import _root_.org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import api.ScDocParamRef
import com.intellij.lang.ASTNode

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocParamRefImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocParamRef{
  override def toString: String = "ScDocParamRef"
}