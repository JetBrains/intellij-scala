package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import base.patterns.ScCaseClause
import statements._
import params.ScParameter
import toplevel.ScTyped
import com.intellij.psi.{PsiMethod, PsiNamedElement}
import types.{ScSubstitutor, ScType, ScFunctionType}
import impl.toplevel.synthetic.ScSyntheticFunction
import resolve.ScalaResolveResult
import base.{ScConstructor, ScReferenceElement}

/**
 * @author ilyas
 *
 * Utility object to calculate expected type of any expression
 */

object ExpectedTypes {
  def expectedExprType(expr: ScExpression): Option[ScType] = {
    expr.getParent match {
      //see SLS[6.11]
      case b: ScBlockExpr => b.lastExpr match {
        case Some(e) if e == expr => b.expectedType
        case _ => None
      }
      //see SLS[6.16]
      case cond: ScIfStmt if cond.condition.getOrElse(null: ScExpression) == expr => Some(types.Boolean)
      case cond: ScIfStmt if cond.elseBranch != None => cond.expectedType
      case cond: ScIfStmt => Some(types.Unit)
      //see SLA[6.22]
      case tb: ScTryBlock => tb.lastExpr match {
        case Some(e) if e == expr => tb.getParent.asInstanceOf[ScTryStmt].expectedType
        case _ => None
      }
      //todo: make catch block an expression with appropriate type and expected type (PartialFunction[Throwable, pt])
      case fb: ScFinallyBlock => Some(types.Unit)
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
      //SLS[6.13]
      case t: ScTypedStmt => {
        t.typeElement match {
          case Some(_) => Some(t.getType)
          case _ => None
        }
      }
      //SLS[6.15]
      case a: ScAssignStmt if a.getRExpression.getOrElse(null: ScExpression) == expr => {
        a.getLExpression match {
          case ref: ScReferenceExpression => {
            ref.resolve match {
              case named: PsiNamedElement => {
                ScalaPsiUtil.nameContext(named) match {
                  case v: ScValue => Some(named.asInstanceOf[ScTyped].calcType)
                  case v: ScVariable => Some(named.asInstanceOf[ScTyped].calcType)
                  case f: ScFunction => None //todo: find functionName_= method and do as argument call expected type
                  case _ => None
                }
              }
              case _ => None
            }
          }
          case call: ScMethodCall => None//todo: as argumets call expected type
          case _ => None
        }
      }
      //SLS[4.1]
      case v: ScPatternDefinition if v.expr == expr => {
        v.typeElement match {
          case Some(_) => Some(v.getType)
          case _ => None
        }
      }
      case v: ScVariableDefinition if v.expr == expr => {
        v.typeElement match {
          case Some(_) => Some(v.getType)
          case _ => None
        }
      }
      //SLS[4.6]
      case v: ScFunctionDefinition if v.body.getOrElse(null: ScExpression) == expr => {
        v.returnTypeElement match {
          case Some(_) => Some(v.returnType)
          case _ => None
        }
      }
      //default parameters
      case param: ScParameter => {
        param.typeElement match {
          case Some(_) => Some(param.calcType)
          case _ => None
        }
      }
      //todo: this cannot have expected type, should be removed
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
}