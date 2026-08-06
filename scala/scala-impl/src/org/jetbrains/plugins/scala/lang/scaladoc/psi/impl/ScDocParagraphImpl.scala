package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocParagraph

class ScDocParagraphImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocParagraph {
  override def toString: String = "ScDocParagraph"
}