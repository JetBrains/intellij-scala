package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocFieldRef

class ScDocFieldRefImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocFieldRef{
  override def toString: String = "DocFieldRef"
}