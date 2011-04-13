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
import lang.resolve.{StdKinds, ScalaResolveResult}
import lang.resolve.processor.{MethodResolveProcessor, ResolveProcessor}
import types.Compatibility.Expression

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
            ScType.extractFunctionType(tp) match {
              case Some(ScFunctionType(rt: ScType, _)) => res += rt
              case None =>
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
      case fb: ScFinallyBlock => Array(types.Unit)
      //see SLS[8.4]
      case c: ScCaseClause => c.getContext.getContext match {
        case m: ScMatchStmt => finalize(m)
        case b: ScBlockExpr if b.isAnonymousFunction => {
          finalize(b).flatMap(tp => ScType.extractFunctionType(tp) match {
            case Some(ScFunctionType(retType, _)) => Array[ScType](retType)
            case _ => Array[ScType]()
          })
        }
        case cb: ScCatchBlock =>
          finalize(cb.getContext.asInstanceOf[ScTryStmt])
        case _ => Array.empty
      }
      //see SLS[6.23]
      case f: ScFunctionExpr => finalize(f).flatMap(tp => ScType.extractFunctionType(tp) match {
        case Some(ScFunctionType(retType, _)) => Array[ScType](retType)
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
        val exprs: Seq[ScExpression] = tuple.exprs
        val actExpr = actualExpr(expr)
        val i = if (actExpr == null) 0 else exprs.findIndexOf(_ == expr)
        val callExpression = tuple.getContext.asInstanceOf[ScInfixExpr].operation
        if (callExpression != null) {
          val tps = callExpression match {
            case ref: ScReferenceExpression =>
              if (!withResolvedFunction) ref.shapeMultiType
              else ref.multiType
            case _ => Array(callExpression.getNonValueType(TypingContext.empty))
          }
          tps.foreach(processArgsExpected(res, expr, i, _, exprs))
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
        val zExpr: ScExpression = expr match {
          case p: ScParenthesisedExpr => p.expr.getOrElse(return Array.empty)
          case _ => expr
        }
        val op = infix.operation
        val tps = if (!withResolvedFunction) op.shapeMultiType else op.multiType
        tps.foreach(processArgsExpected(res, zExpr, 0, _, Seq(zExpr)))
        res.toArray
      }
      //SLS[4.1]
      case v: ScPatternDefinition if v.expr == expr.getSameElementInContext => {
        v.typeElement match {
          case Some(_) => Array(v.getType(TypingContext.empty).getOrElse(Any))
          case _ => Array.empty
        }
      }
      case v: ScVariableDefinition if v.expr == expr.getSameElementInContext => {
        v.typeElement match {
          case Some(_) => Array(v.getType(TypingContext.empty).getOrElse(Any))
          case _ => Array.empty
        }
      }
      //SLS[4.6]
      case v: ScFunctionDefinition if (v.body match {
        case None => false
        case Some(b) => b == expr.getSameElementInContext
      }) => {
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
        val fun: ScFunction = PsiTreeUtil.getContextOfType(ret, true, classOf[ScFunction])
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
        val exprs: Seq[ScExpression] = args.exprs
        val actExpr = actualExpr(expr)
        val i = if (actExpr == null) 0 else exprs.findIndexOf(_ == actExpr)
        val callExpression = args.callExpression
        if (callExpression != null) {
          var tps: Array[TypeResult[ScType]] = callExpression match {
            case ref: ScReferenceExpression =>
              if (!withResolvedFunction) ref.shapeMultiType
              else ref.multiType
            case gen: ScGenericCall =>
              if (!withResolvedFunction) gen.shapeMultiType
              else gen.multiType
            case _ => Array(callExpression.getNonValueType(TypingContext.empty))
          }
          args.getParent match {
            case call: ScMethodCall =>
              tps = tps.map(call.updateAccordingToExpectedType(_))
            case _ => //todo: infix calls? or to change according compiler, like syntax suger, see: ScForStmt
          }
          tps.foreach(processArgsExpected(res, expr, i, _, exprs))
        } else {
          //it's constructor
          args.getParent match {
            case constr: ScConstructor => {
              val j = constr.arguments.indexOf(args)
              constr.shapeMultiType(j).foreach(processArgsExpected(res, expr, i, _, exprs))
            }
            case s: ScSelfInvocation => {
              val j = s.arguments.indexOf(args)
              s.shapeMultiType(j).foreach(processArgsExpected(res, expr, i, _, exprs))
            }
            case _ =>
          }
        }
        res.toArray
      }
      case b: ScBlock if b.getContext.isInstanceOf[ScTryBlock]
              || b.getContext.getContext.getContext.isInstanceOf[ScCatchBlock]
              || b.getContext.isInstanceOf[ScCaseClause] => b.lastExpr match {
        case Some(e) if e == expr => finalize(b)
        case _ => Array.empty
      }
      case _ => Array.empty
    }
  }

  private def processArgsExpected(res: ArrayBuffer[ScType], expr: ScExpression, i: Int, tp: TypeResult[ScType],
                                  exprs: Seq[ScExpression]) {
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
          val tp = ScParameterizedType(ScType.designator(seqClass), Seq(params(params.length - 1).paramType))
          res += tp
        }
      } else res += p
    }
    tp match {
      case Success(ScMethodType(_, params, _), _) => {
        if (params.length == 1 && !params.apply(0).isRepeated && exprs.length > 1) {
          params.apply(0).paramType match {
            case ScTupleType(args) => applyForParams(args.map(Parameter("", _, false, false, false)))
            case p: ScParameterizedType if p.getTupleType != None =>
              applyForParams(p.getTupleType.get.components.map(Parameter("", _, false, false, false)))
            case _ =>
          }
        } else applyForParams(params)
      }
      case Success(t@ScTypePolymorphicType(ScMethodType(_, params, _), typeParams), _) => {
        val subst = t.abstractTypeSubstitutor
        val newParams = params.map(p => p.copy(paramType = subst.subst(p.paramType)))
        if (newParams.length == 1 && !newParams.apply(0).isRepeated && exprs.length > 1) {
          newParams.apply(0).paramType match {
            case ScTupleType(args) => applyForParams(args.map(Parameter("", _, false, false, false)))
            case p: ScParameterizedType if p.getTupleType != None =>
              applyForParams(p.getTupleType.get.components.map(Parameter("", _, false, false, false)))
            case _ =>
          }
        } else applyForParams(newParams)
      }
      case Success(t@ScTypePolymorphicType(anotherType, typeParams), _) => {
        import Expression._
        val applyProc =
          new MethodResolveProcessor(expr, "apply", List(exprs), Seq.empty, StdKinds.methodsOnly, isShapeResolve = true)
        applyProc.processType(anotherType, expr)
        val cand = applyProc.candidates
        if (cand.length == 1) {
          cand(0) match {
            case ScalaResolveResult(fun: ScFunction, s) => {
              val subst = s followed t.abstractTypeSubstitutor
              processArgsExpected(res, expr, i, Success(subst.subst(fun.methodType), Some(expr)), exprs)
            }
            case _ =>
          }
        }
      }
      case Success(anotherType, _) => {
        import Expression._
        val applyProc =
          new MethodResolveProcessor(expr, "apply", List(exprs), Seq.empty, StdKinds.methodsOnly, isShapeResolve = true)
        applyProc.processType(anotherType, expr)
        val cand = applyProc.candidates
        if (cand.length == 1) {
          cand(0) match {
            case ScalaResolveResult(fun: ScFunction, subst) => {
              processArgsExpected(res, expr, i, Success(subst.subst(fun.methodType), Some(expr)), exprs)
            }
            case _ =>
          }
        }
      }
      case _ =>
    }
  }

  private def actualExpr(expr: PsiElement): PsiElement = {
    val next = expr.getNextSibling
    if (next != null) return next.getPrevSibling
    val prev = expr.getPrevSibling
    if (prev != null) return prev.getNextSibling
    return null
  }
}