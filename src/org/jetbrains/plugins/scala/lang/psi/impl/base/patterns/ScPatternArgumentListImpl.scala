package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr

/**
* @author ilyas
*/

class ScPatternArgumentListImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPatternArgumentList{

  override def toString: String = "Pattern Argument List"

  def patterns = {
    val children: Seq[ScPattern] = findChildrenByClassScala[ScPattern](classOf[ScPattern])
    val grandChildrenInBlockExpr: Seq[ScPattern] = this.getChildren.filter{_.isInstanceOf[ScBlockExpr]}.flatMap{s => s.getChildren.filter(_.isInstanceOf[ScPattern]).map{_.asInstanceOf[ScPattern]}}
    children ++ grandChildrenInBlockExpr
  }

}