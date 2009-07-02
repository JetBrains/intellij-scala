package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import api.expr.{ScBlockExpr, ScMatchStmt, ScCatchBlock}
import api.statements.{ScValue, ScVariable}
import api.base.patterns._
import api.base.ScPatternList
import psi.types._
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.lang.ASTNode
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

  def subpatterns : Seq[ScPattern] = findChildrenByClass(classOf[ScPattern])

  def expectedType :Option[ScType] = getParent match {
    case list : ScPatternList => list.getParent match {
      case _var : ScVariable => Some(_var.getType)
      case _val : ScValue => Some(_val.getType)
    }
    case argList : ScPatternArgumentList => {
      argList.getParent match {
        case constr : ScConstructorPattern => constr.bindParamTypes match {
          case Some(ts) =>
            for ((p, t) <- constr.args.patterns.elements zip ts.elements) {
              if (p == this) return Some(t)
            }
            None
          case _ => None
        }
      }
    }
    case patternList : ScPatterns => patternList.getParent match {
      case tuple : ScTuplePattern => tuple.expectedType match {
        case Some(ScTupleType(comps)) => {
          for((t, p) <- comps.elements.zip(patternList.patterns.elements)) {
            if (p == this) return Some(t)
          }
          None
        }
        case _ => None
      }
    }
    case clause : ScCaseClause => clause.getParent/*clauses*/.getParent match {
      case matchStat : ScMatchStmt => matchStat.expr match {
        case Some(e) => Some(e.cachedType)
        case _ => None
      }
      case _ : ScCatchBlock => {
        val thr = JavaPsiFacade.getInstance(getProject).findClass("java.lang.Throwable")
        if (thr != null) Some(new ScDesignatorType(thr)) else None 
      }
      case b : ScBlockExpr => b.expectedType match { //l1.zip(l2) {case (a,b) =>}
        case Some(ScFunctionType(ret, params)) => Some(new ScTupleType(params))
        case _ => None
      }
    }
    case _ => None //todo
  }
}