package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._

class ScTypePatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScTypePattern{
  override def toString: String = "TypePattern"
}