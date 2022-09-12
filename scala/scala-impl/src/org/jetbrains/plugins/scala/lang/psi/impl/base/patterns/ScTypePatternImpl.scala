package org.jetbrains.plugins.scala.lang.psi.impl.base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScTypePatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypePattern{
  override def toString: String = "TypePattern"
}