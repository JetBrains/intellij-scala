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
            ref.bind match {
              case Some(ScalaResolveResult(named: PsiNamedElement, subst: ScSubstitutor)) => {
                ScalaPsiUtil.nameContext(named) match {
                  case v: ScValue => Some(named.asInstanceOf[ScTyped].calcType)
                  case v: ScVariable => Some(named.asInstanceOf[ScTyped].calcType)
                  case f: ScFunction => None //todo: find functionName_= method and do as argument call expected type
                  case p: ScParameter => {
                    //for named parameters
                    Some(subst.subst(p.calcType))
                  }
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
      case _ => None
    }

  }

  def expectedForArguments(expr: ScExpression): Option[ScArgumentExprList] = {
    expr.getParent match {
      case b: ScBlockExpr => b.lastExpr match {
        case Some(e) if e == expr => expectedForArguments(b)
        case _ => None
      }
      case cond: ScIfStmt if cond.condition.getOrElse(null: ScExpression) == expr => None
      case cond: ScIfStmt if cond.elseBranch != None => expectedForArguments(cond)
      case cond: ScIfStmt => None
      case tb: ScTryBlock => tb.lastExpr match {
        case Some(e) if e == expr => expectedForArguments(tb.getParent.asInstanceOf[ScTryStmt])
        case _ => None
      }
      case fb: ScFinallyBlock => None
      case c: ScCaseClause => c.getParent.getParent match {
        case m: ScMatchStmt => expectedForArguments(m)
        case _ => None
      }
      case f: ScFunctionExpr => None
      case t: ScTypedStmt => None
      case a: ScAssignStmt if a.getRExpression.getOrElse(null: ScExpression) == expr => {
        a.getParent match {
          case args: ScArgumentExprList => {
            a.getLExpression match {
              case ref: ScReferenceExpression => {
                val name = ref.refName
                for (application <- args.possibleApplications 
                  if application.find((t: (String, ScType)) => t._1 == name) != None) return Some(args)
                None
              }
              case _ => None
            }
          }
          case _ => None
        }
      }
      case v: ScPatternDefinition if v.expr == expr => None
      case v: ScVariableDefinition if v.expr == expr => None
      case v: ScFunctionDefinition if v.body.getOrElse(null: ScExpression) == expr => None
      case param: ScParameter => None
      case args: ScArgumentExprList => Some(args)
      case _ => None
    }

  }
}