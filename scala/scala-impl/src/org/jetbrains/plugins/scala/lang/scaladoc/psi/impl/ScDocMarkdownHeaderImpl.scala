package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocMarkdownHeader

class ScDocMarkdownHeaderImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocMarkdownHeader {
  override def toString: String = "ScDocMarkdownHeader"
}
