package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._

class ScPatternsImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPatterns{
  override def toString: String = "ArgumentPatterns"

  override def patterns: Seq[ScPattern] = findChildrenByClass(classOf[ScPattern]).toSeq
}