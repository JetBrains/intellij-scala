package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl


import _root_.org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import api.ScDocMethodRef
import com.intellij.lang.ASTNode

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocMethodRefImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocMethodRef{
  override def toString: String = "DocMethodRef"
}