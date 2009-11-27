package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import base.patterns.ScCaseClause
import statements._
import params.ScParameter
import toplevel.ScTypedDefinition
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import lang.resolve.ScalaResolveResult
import base.{ScConstructor, ScReferenceElement}
import collection.mutable.ArrayBuffer
import types._
import com.intellij.psi.util.PsiTreeUtil
import result.{Success, TypingContext}
import base.types.{ScSequenceArg, ScTypeElement}
import com.intellij.psi._

/**
 * @author ilyas
 *
 * Utility object to calculate expected type of any expression
 */

private[expr] object ExpectedTypes {
  def expectedExprType(expr: ScExpression): Option[ScType] = {
    val types = expr.expectedTypes
    types.length match {
      case 1 => Some(types(0))
      case _ => None
    }
  }
  def expectedExprTypes(expr: ScExpression): Array[ScType] = {
    //this method needs to replace expected type to return type if it's placholder function expression
    def finalize(expr: ScExpression): Array[ScType] = {
      ScUnderScoreSectionUtil.underscores(expr).length match {
        case 0 => expectedExprTypes(expr)
        case _ => {
          val res = new ArrayBuffer[ScType]
          for (tp <- expectedExprTypes(expr)) {
            tp match {
              case ScFunctionType(rt: ScType, _) => res += rt
              case ScParameterizedType(des, args) => {
                ScType.extractClassType(des) match {
                  case Some((clazz: PsiClass, _)) if clazz.getQualifiedName.startsWith("scala.Function") => res += args(args.length - 1)
                  case _ =>
                }
              }
              case _ =>
            }
          }
          res.toArray
        }
      }
    }
    expr.getParent match {
      //see SLS[6.11]
      case b: ScBlockExpr => b.lastExpr match {
        case Some(e) if e == expr => finalize(b)
        case _ => Array.empty
      }
      //see SLS[6.16]
      case cond: ScIfStmt if cond.condition.getOrElse(null: ScExpression) == expr => Array(types.Boolean)
      case cond: ScIfStmt if cond.elseBranch != None => finalize(cond)
      //see SLA[6.22]
      case tb: ScTryBlock => tb.lastExpr match {
        case Some(e) if e == expr => finalize(tb.getParent.asInstanceOf[ScTryStmt])
        case _ => Array.empty
      }
      //todo: make catch block an expression with appropriate type and expected type (PartialFunction[Throwable, pt])
      case fb: ScFinallyBlock => Array(types.Unit)
      //see SLS[8.4]
      case c: ScCaseClause => c.getParent.getParent match {
        case m: ScMatchStmt => finalize(m)
        case _ => Array.empty
      }
      //see SLS[6.23]
      case f: ScFunctionExpr => finalize(f).flatMap(_ match {
        case ScFunctionType(retType, _) => Array[ScType](retType)
        case ScParameterizedType(des, args) => {
          ScType.extractClassType(des) match {
            case Some((clazz: PsiClass, _)) if clazz.getQualifiedName.startsWith("scala.Function") => Array[ScType](args(args.length - 1))
            case _ => Array[ScType]()
          }
        }
        case _ => Array[ScType]()
      })

      case t: ScTypedStmt if t.getLastChild.isInstanceOf[ScSequenceArg] => {
        expectedExprTypes(t)
      }
      //SLS[6.13]
      case t: ScTypedStmt => {
        t.typeElement match {
          case Some(_) => Array(t.getType(TypingContext.empty).getOrElse(Any))
          case _ => Array.empty
        }
      }
      //SLS[6.15]
      case a: ScAssignStmt if a.getRExpression.getOrElse(null: ScExpression) == expr => {
        a.getLExpression match {
          case ref: ScReferenceExpression => {
            ref.bind match {
              case Some(ScalaResolveResult(named: PsiNamedElement, subst: ScSubstitutor)) => {
                ScalaPsiUtil.nameContext(named) match {
                  case v: ScValue => Array(named.asInstanceOf[ScTypedDefinition].getType(TypingContext.empty).getOrElse(Any))
                  case v: ScVariable => Array(named.asInstanceOf[ScTypedDefinition].getType(TypingContext.empty).getOrElse(Any))
                  case f: ScFunction => Array.empty //todo: find functionName_= method and do as argument call expected type
                  case p: ScParameter => {
                    //for named parameters
                    Array(subst.subst(p.getType(TypingContext.empty).getOrElse(Any)))
                  }
                  case _ => Array.empty
                }
              }
              case _ => Array.empty
            }
          }
          case call: ScMethodCall => Array.empty//todo: as argumets call expected type
          case _ => Array.empty
        }
      }
      case tuple: ScTuple => {
        val buffer = new ArrayBuffer[ScType]
        val index = tuple.exprs.indexOf(expr)
        for (tp: ScType <- expectedExprTypes(tuple)) {
          tp match {
            case ScTupleType(comps) if comps.length == tuple.exprs.length => {
              buffer += comps(index)
            }
            case _ =>
          }
        }
        buffer.toArray
      }
      //SLS[4.1]
      case v: ScPatternDefinition if v.expr == expr => {
        v.typeElement match {
          case Some(_) => Array(v.getType(TypingContext.empty).getOrElse(Any))
          case _ => Array.empty
        }
      }
      case v: ScVariableDefinition if v.expr == expr => {
        v.typeElement match {
          case Some(_) => Array(v.getType(TypingContext.empty).getOrElse(Any))
          case _ => Array.empty
        }
      }
      //SLS[4.6]
      case v: ScFunctionDefinition if v.body.getOrElse(null: ScExpression) == expr => {
        v.returnTypeElement match {
          case Some(_) => Array(v.returnType.getOrElse(Any))
          case _ => Array.empty
        }
      }
      //default parameters
      case param: ScParameter => {
        param.typeElement match {
          case Some(_) => Array(param.getType(TypingContext.empty).getOrElse(Any))
          case _ => Array.empty
        }
      }
      case ret: ScReturnStmt => {
        val fun: ScFunction = PsiTreeUtil.getParentOfType(ret, classOf[ScFunction])
        if (fun == null) return Array.empty
        fun.returnTypeElement match {
          case Some(rte: ScTypeElement) => {
            fun.returnType match {
              case Success(rt: ScType, _) => return Array(rt)
              case _ => Array.empty
            }
          }
          case None => return Array.empty
        }
      }
      case args: ScArgumentExprList => {
        val res = new ArrayBuffer[ScType]
        expr match {
          case typed: ScTypedStmt if typed.getLastChild.isInstanceOf[ScSequenceArg] &&
                  args.exprs.lastOption == Some(expr) => {
            for (application: Array[(String, ScType)] <- args.possibleApplications) {
              if (application.length == args.exprs.length) {
                //todo: add possibility to check if last param is repeated
                val seqClass: PsiClass = JavaPsiFacade.getInstance(expr.getProject).findClass("scala.collection.Seq", expr.getResolveScope)
                if (seqClass != null) {
                  val tp = ScParameterizedType(ScDesignatorType(seqClass), Seq(application(application.length - 1)._2))
                  res += tp
                }
              }
            }
          }
          case _ => {
            val i: Int = args.exprs.findIndexOf(_ == expr)
            for (application: Array[(String, ScType)] <- args.possibleApplications) {
              if (application.length > i && i >=0) {
                res += application(i)._2
              }
            }
          }
        }
        res.toArray
      }
      case _ => Array.empty
    }
  }
}