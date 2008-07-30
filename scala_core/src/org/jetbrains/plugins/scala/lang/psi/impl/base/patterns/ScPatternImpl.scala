package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import api.statements.{ScValue, ScVariable}
import api.base.patterns._
import api.toplevel.ScTyped
import api.base.ScPatternList
import com.intellij.lang.ASTNode
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.psi._
import com.intellij.psi.scope._

class ScPatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPattern {
  def bindings : Seq[ScBindingPattern] = {
    val b = new ArrayBuffer[ScBindingPattern]
    _bindings(this, b)
    b
  }

  private def _bindings(p : ScPattern, b : ArrayBuffer[ScBindingPattern]) : Unit = {
    p match {
      case binding: ScBindingPattern => b += binding
      case _ =>
    }

    for (sub <- p.subpatterns) {
      _bindings(sub, b)
    }
  }

  def subpatterns : Seq[ScPattern] = {
    if (!this.isInstanceOf[ScTuplePattern])
      findChildrenByClass(classOf[ScPattern])
    else
      findChildByClass(classOf[ScPatterns]).patterns
  }

  def expectedType = getParent match {
    case list : ScPatternList => list.getParent match {
      case _var : ScVariable => Some(_var.getType)
      case _val : ScValue => Some(_val.getType)
    }
    case _ => None //todo
  }
}