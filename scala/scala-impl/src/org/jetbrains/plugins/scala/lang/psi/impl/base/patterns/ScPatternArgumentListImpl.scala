package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr

class ScPatternArgumentListImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPatternArgumentList{

  override def toString: String = "Pattern Argument List"

  override def patterns: Seq[ScPattern] = {
    val children = findChildren[ScPattern]
    val grandChildrenInBlockExpr = children
      .iterator
      .filterByType[ScBlockExpr]
      .flatMap(s => s.children.filterByType[ScPattern])
    children ++ grandChildrenInBlockExpr
  }

}