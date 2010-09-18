package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import base.patterns.ScCaseClause
import statements._
import params.ScParameter
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import base.{ScConstructor, ScReferenceElement}
import collection.mutable.ArrayBuffer
import types._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi._
import nonvalue.{TypeParameter, Parameter, ScTypePolymorphicType, ScMethodType}
import toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import result.{TypeResult, Success, TypingContext}
import base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScSequenceArg, ScTypeElement}
import collection.immutable.HashMap
import lang.resolve.processor.ResolveProcessor
import lang.resolve.{StdKinds, ScalaResolveResult}

/**
 * @author ilyas
 *
 * Utility object to calculate expected type of any expression
 */

private[expr] object ExpectedTypes {
  /**
   * Do not use this method inside of resolve or type inference.
   * Using this leads to SOE.
   */
  def smartExpectedType(expr: ScExpression): Option[ScType] = {
    val types = expectedExprTypes(expr, true)
    types.length match {
      case 1 => Some(types(0))
      case _ => None
    }
  }
  
  def expectedExprType(expr: ScExpression): Option[ScType] = {
    val types = expr.expectedTypes
    types.length match {
      case 1 => Some(types(0))
      case _ => None
    }
  }
  def expectedExprTypes(expr: ScExpression, withResolvedFunction: Boolean = false): Array[ScType] = {
    //this method needs to replace expected type to return type if it's placholder function expression
    def finalize(expr: ScExpression): Array[ScType] = {
      ScUnderScoreSectionUtil.underscores(expr).length match {
        case 0 => expr.expectedTypes
        case _ => {
          val res = new ArrayBuffer[ScType]
          for (tp <- expr.expectedTypes) {
            tp match {
              case ScFunctionType(rt: ScType, _) => res += rt
              case ScParameterizedType(des, args) => {
                ScType.extractClass(des) match {
                  case Some(clazz) if clazz.getQualifiedName.startsWith("scala.Function") => res += args(args.length - 1)
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
    expr.getContext match {
      case p: ScParenthesisedExpr => p.expectedTypes
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
        case Some(e) if e == expr => finalize(tb.getContext.asInstanceOf[ScTryStmt])
        case _ => Array.empty
      }
      //todo: make catch block an expression with appropriate type and expected type (PartialFunction[Throwable, pt])
      case fb: ScFinallyBlock => Array(types.Unit)
      //see SLS[8.4]
      case c: ScCaseClause => c.getContext.getContext match {
        case m: ScMatchStmt => finalize(m)
        case _ => Array.empty
      }
      //see SLS[6.23]
      case f: ScFunctionExpr => finalize(f).flatMap(_ match {
        case ScFunctionType(retType, _) => Array[ScType](retType)
        case ScParameterizedType(des, args) => {
          ScType.extractClass(des) match {
            case Some(clazz) if clazz.getQualifiedName.startsWith("scala.Function") => Array[ScType](args(args.length - 1))
            case _ => Array[ScType]()
          }
        }
        case _ => Array[ScType]()
      })

      case t: ScTypedStmt if t.getLastChild.isInstanceOf[ScSequenceArg] => {
        t.expectedTypes
      }
      //SLS[6.13]
      case t: ScTypedStmt => {
        t.typeElement match {
          case Some(te) => Array(te.getType(TypingContext.empty).getOrElse(Any))
          case _ => Array.empty
        }
      }
      //SLS[6.15]
      case a: ScAssignStmt if a.getRExpression.getOrElse(null: ScExpression) == expr => {
        a.getLExpression match {
          case ref: ScReferenceExpression if !a.getParent.isInstanceOf[ScArgumentExprList] => {
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
          case ref: ScReferenceExpression => expectedExprTypes(a)
          case call: ScMethodCall => Array.empty//todo: as argumets call expected type
          case _ => Array.empty
        }
      }
      //method application
      case tuple: ScTuple if tuple.isCall => {
        val res = new ArrayBuffer[ScType]
        val i = tuple.exprs.findIndexOf(_ == expr)
        val callExpression = tuple.getContext.asInstanceOf[ScInfixExpr].operation
        if (callExpression != null) {
          val tp = callExpression match {
            case ref: ScReferenceExpression =>
              if (!withResolvedFunction) ref.shapeType
              else ref.getNonValueType(TypingContext.empty)
            case _ => callExpression.getNonValueType(TypingContext.empty)
          }
          processArgsExpected(res, expr, i, tp, tuple.exprs.length - 1)
        }
        res.toArray
      }
      case tuple: ScTuple => {
        val buffer = new ArrayBuffer[ScType]
        val index = tuple.exprs.indexOf(expr)
        for (tp: ScType <- tuple.expectedTypes) {
          tp match {
            case ScTupleType(comps) if comps.length == tuple.exprs.length => {
              buffer += comps(index)
            }
            case _ =>
          }
        }
        buffer.toArray
      }
      case infix: ScInfixExpr if (infix.isLeftAssoc && infix.lOp == expr) ||
              (!infix.isLeftAssoc && infix.rOp == expr) => {
        val res = new ArrayBuffer[ScType]
        val zExpr:ScExpression = expr match {
          case p: ScParenthesisedExpr => p.expr.getOrElse(return Array.empty)
          case _ => expr
        }
        val op = infix.operation
        val tp = if (!withResolvedFunction) op.shapeType else op.getNonValueType(TypingContext.empty)
        processArgsExpected(res, zExpr, 0, tp, 1)
        res.toArray
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
        v.getInheritedReturnType.toArray
      }
      //default parameters
      case param: ScParameter => {
        param.typeElement match {
          case Some(_) => Array(param.getType(TypingContext.empty).getOrElse(Any))
          case _ => Array.empty
        }
      }
      case ret: ScReturnStmt => {
        val fun: ScFunction = PsiTreeUtil.getContextOfType(ret, classOf[ScFunction], true)
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
        val i = args.exprs.findIndexOf(_ == expr)
        val callExpression = args.callExpression
        if (callExpression != null) {
          val tp = callExpression match {
            case ref: ScReferenceExpression =>
              if (!withResolvedFunction) ref.shapeType
              else ref.getNonValueType(TypingContext.empty)
            case gen: ScGenericCall =>
              if (!withResolvedFunction) gen.shapeType
              else gen.getNonValueType(TypingContext.empty)
            case _ => callExpression.getNonValueType(TypingContext.empty)
          }
          processArgsExpected(res, expr, i, tp, args.exprs.length - 1)
        } else {
          //it's constructor
          args.getParent match {
            case constr: ScConstructor => {
              val j = constr.arguments.indexOf(args)
              processArgsExpected(res, expr, i, constr.shapeType(j), args.exprs.length - 1)
            }
            case _ =>
          }
        }
        res.toArray
      }
      case _ => Array.empty
    }
  }

  private def processArgsExpected(res: ArrayBuffer[ScType], expr: ScExpression, i: Int, tp: TypeResult[ScType],
                                  length: Int) {
    def applyForParams(params: Seq[Parameter]) {
      val p: ScType =
        if (i >= params.length && params.length > 0 && params(params.length - 1).isRepeated)
          params(params.length - 1).paramType
        else if (i >= params.length) Nothing
        else params(i).paramType
      if (expr.isInstanceOf[ScAssignStmt]) {
        val assign = expr.asInstanceOf[ScAssignStmt]
        val lE = assign.getLExpression
        lE match {
          case ref: ScReferenceExpression if ref.qualifier == None => {
            val name = ref.refName
            params.find(_.name == name) match {
              case Some(param) => res += param.paramType
              case _ => res += p
            }
          }
          case _ => res += p
        }
      } else if (expr.isInstanceOf[ScTypedStmt] && expr.getLastChild.isInstanceOf[ScSequenceArg] && params.length > 0) {
        val seqClass: PsiClass = JavaPsiFacade.getInstance(expr.getProject).findClass("scala.collection.Seq", expr.getResolveScope)
        if (seqClass != null) {
          val tp = ScParameterizedType(ScDesignatorType(seqClass), Seq(params(params.length - 1).paramType))
          res += tp
        }
      } else res += p
    }
    tp match {
      case Success(ScMethodType(_, params, _), _) => {
        applyForParams(params)
      }
      case Success(t@ScTypePolymorphicType(ScMethodType(_, params, _), typeParams), _) => {
        val subst = t.abstractTypeSubstitutor
        val newParams = params.map(p => Parameter(p.name, subst.subst(p.paramType), p.isDefault, p.isRepeated))
        applyForParams(newParams)
      }
      case Success(t@ScTypePolymorphicType(anotherType, typeParams), _) => {

      }
      case Success(anotherType, _) => {
        val applyProc = new ResolveProcessor(StdKinds.methodsOnly, expr, "apply")
        applyProc.processType(anotherType, expr)
        val cand = applyProc.candidates
        if (cand.length == 1) {
          cand(0) match {
            case ScalaResolveResult(fun: ScFunction, subst) => {
              processArgsExpected(res, expr, i, Success(subst.subst(fun.methodType), Some(expr)), length)
            }
            case _ =>
          }
        }
      }
      case _ =>
    }
  }
}