package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocBlockQuote

class ScDocBlockQuoteImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocBlockQuote {
  override def toString: String = "ScDocQuote"
}
