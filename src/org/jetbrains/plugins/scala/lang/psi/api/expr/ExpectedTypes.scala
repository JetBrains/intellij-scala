package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import base.patterns.ScCaseClause
import resolve.ScalaResolveResult
import statements.ScFunction
import types.{ScType, ScFunctionType}

/**
 * @author ilyas
 *
 * Utility object to calculate expected type of any expression
 */

object ExpectedTypes {
  def expectedExprType(expr: ScExpression): Option[ScType] = expr.getParent match {
  //see SLS[6.11]
    case b: ScBlockExpr => b.lastExpr match {
      case Some(e) if e eq expr => b.expectedType
      case _ => None
    }
    //see SLS[6.16]
    case cond: ScIfStmt => cond.expectedType
    //see SLA[6.22]
    case tb: ScTryBlock => tb.lastExpr match {
      case Some(e) if e eq expr => tb.expectedType
      case _ => None
    }
    //see SLS[8.4]
    case c: ScCaseClause => c.getParent.getParent match {
      case m: ScMatchStmt => m.expectedType
      case _ => None
    }
    //see SLS[6.23]
    case f: ScFunctionExpr => f.expectedType match { //There is only one way to be fun's child - it's result
      case Some(ScFunctionType(rt, _)) => Some(rt)
      case _ => None
    }
    //...
    case args: ScArgumentExprList => args.getParent match {
      case mc: ScMethodCall => {
        None
        /*
        val argLists = mc.allArgumentExprLists

        def invoked(m: ScMethodCall): ScExpression = m.getInvokedExpr match {
          case mc1: ScMethodCall => invoked(mc1)
          case e => e
        }

        val inv = invoked(mc)
        //todo To be replaced
        inv match {
          case e: ScReferenceExpression => e.bind.map {
            case ScalaResolveResult(element, s) => {
              element match {
                case f: ScFunction => {
                  val ftype = s.subst(f.calcType)
                  ftype match {
                  //todo HACK remove me as soon as possible!
                    case ScFunctionType(_, params) if argLists.length == 1 => {
                      val i = argLists(0).exprs.indexOf(this)
                      if (i >= 0 && i < params.length) params(i) else types.Nothing
                    }
                    case _ => types.Nothing
                  }
                }
                case _ => types.Nothing
              }
            }
          }
          case _ => None
        }
*/
      }
      case _ => None
    }
    case _ => None
  }


}