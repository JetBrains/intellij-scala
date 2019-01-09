package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl


import _root_.org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocMethodRef

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocMethodRefImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocMethodRef{
  override def toString: String = "DocMethodRef"
}