package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocInnerCodeElement

class ScDocInnerCodeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocInnerCodeElement {
  override def toString = "InnerCodeElement"
}