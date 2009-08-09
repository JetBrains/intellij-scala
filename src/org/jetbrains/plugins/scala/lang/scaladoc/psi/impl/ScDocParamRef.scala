package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl


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