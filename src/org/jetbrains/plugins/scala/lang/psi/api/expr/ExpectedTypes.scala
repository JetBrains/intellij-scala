package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import base.patterns.ScCaseClause
import statements._
import params.ScParameter
import toplevel.ScTyped
import types.{ScSubstitutor, ScType, ScFunctionType}
import impl.toplevel.synthetic.ScSyntheticFunction
import resolve.ScalaResolveResult
import base.{ScConstructor, ScReferenceElement}
import com.intellij.psi.{PsiElement, PsiMethod, PsiNamedElement}

/**
 * @author ilyas
 *
 * Utility object to calculate expected type of any expression
 */

object ExpectedTypes {
  def expectedExprType(expr: ScExpression): Option[ScType] = {
    //this method needs to replace expected type to return type if it's placholder function expression
    def finalize(expr: ScExpression): Option[ScType] = {
      ScUnderScoreSectionUtil.underscores(expr).length match {
        case 0 => expr.expectedType
        case _ => {
          expr.expectedType match {
            case Some(ScFunctionType(rt: ScType, _)) => Some(rt)
            case x => None
          }
        }
      }
    }
    expr.getParent match {
      //see SLS[6.11]
      case b: ScBlockExpr => b.lastExpr match {
        case Some(e) if e == expr => finalize(b)
        case _ => None
      }
      //see SLS[6.16]
      case cond: ScIfStmt if cond.condition.getOrElse(null: ScExpression) == expr => Some(types.Boolean)
      case cond: ScIfStmt if cond.elseBranch != None => finalize(cond)
      case cond: ScIfStmt => Some(types.Unit)
      //see SLA[6.22]
      case tb: ScTryBlock => tb.lastExpr match {
        case Some(e) if e == expr => finalize(tb.getParent.asInstanceOf[ScTryStmt])
        case _ => None
      }
      //todo: make catch block an expression with appropriate type and expected type (PartialFunction[Throwable, pt])
      case fb: ScFinallyBlock => Some(types.Unit)
      //see SLS[8.4]
      case c: ScCaseClause => c.getParent.getParent match {
        case m: ScMatchStmt => finalize(m)
        case _ => None
      }
      //see SLS[6.23]
      case f: ScFunctionExpr => finalize(f)
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

  /**
   * this method returns arguments for which we can try to evaluate expected type
   * or returns expression with placeholders for which expectedForArguments not null
   */
  def expectedForArguments(expr: ScExpression): Option[PsiElement] = {
    def finalize(expr: ScExpression): Option[PsiElement] = {
      ScUnderScoreSectionUtil.underscores(expr).length match {
        case 0 => expectedForArguments(expr)
        case _ => expectedForArguments(expr) match {
          case Some(e) => Some(expr)
          case _ => None
        }
      }
    }
    expr.getParent match {
      case b: ScBlockExpr => b.lastExpr match {
        case Some(e) if e == expr => finalize(b)
        case _ => None
      }
      case cond: ScIfStmt if cond.condition.getOrElse(null: ScExpression) == expr => None
      case cond: ScIfStmt if cond.elseBranch != None => finalize(cond)
      case cond: ScIfStmt => None
      case tb: ScTryBlock => tb.lastExpr match {
        case Some(e) if e == expr => finalize(tb.getParent.asInstanceOf[ScTryStmt])
        case _ => None
      }
      case fb: ScFinallyBlock => None
      case c: ScCaseClause => c.getParent.getParent match {
        case m: ScMatchStmt => finalize(m)
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