package org.jetbrains.plugins.scala.lang.psi.impl.base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScPatternsImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPatterns{
  override def toString: String = "ArgumentPatterns"

  override def patterns: Seq[ScPattern] = findChildrenByClass(classOf[ScPattern]).toSeq
}