package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl


import _root_.org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import api.ScDocInlinedTag
import com.intellij.lang.ASTNode

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocInlinedTagImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocInlinedTag{
  override def toString: String = "DocInlinedTag"
}