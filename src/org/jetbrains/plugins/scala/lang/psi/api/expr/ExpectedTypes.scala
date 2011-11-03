package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import base.patterns.ScCaseClause
import statements._
import params.ScParameter
import base.ScConstructor
import collection.mutable.ArrayBuffer
import types._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi._
import nonvalue.{Parameter, ScTypePolymorphicType, ScMethodType}
import toplevel.ScTypedDefinition
import result.{TypeResult, Success, TypingContext}
import base.types.{ScSequenceArg, ScTypeElement}
import lang.resolve.{StdKinds, ScalaResolveResult}
import lang.resolve.processor.MethodResolveProcessor
import types.Compatibility.Expression
import com.intellij.openapi.progress.ProgressManager
import toplevel.imports.usages.ImportUsed
import caches.CachesUtil
import implicits.ScImplicitlyConvertible

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
  def smartExpectedType(expr: ScExpression, fromUnderscore: Boolean = true): Option[ScType] =
    smartExpectedTypeEx(expr, fromUnderscore).map(_._1)

  def smartExpectedTypeEx(expr: ScExpression, fromUnderscore: Boolean = true): Option[(ScType, Option[ScTypeElement])] = {
    val types = expectedExprTypes(expr, true, fromUnderscore)
    types.length match {
      case 1 => Some(types(0))
      case _ => None
    }
  }
  
  def expectedExprType(expr: ScExpression, fromUnderscore: Boolean = true): Option[(ScType, Option[ScTypeElement])] = {
    val types = expr.expectedTypesEx(fromUnderscore)
    types.length match {
      case 1 => Some(types(0))
      case _ => None
    }
  }

  implicit def typeToPair(tpe: ScType): (ScType, Option[ScTypeElement]) = (tpe, None) // TODO jzaugg remove

  /**
   * @return (expectedType, expectedTypeElement)
   */
  def expectedExprTypes(expr: ScExpression, withResolvedFunction: Boolean = false,
                        fromUnderscore: Boolean = true): Array[(ScType, Option[ScTypeElement])] = {
    val result: Array[(ScType, Option[ScTypeElement])] = expr.getContext match {
      case p: ScParenthesisedExpr => p.expectedTypesEx(fromUnderscore)
      //see SLS[6.11]
      case b: ScBlockExpr => b.lastExpr match {
        case Some(e) if e == expr => b.expectedTypesEx(true)
        case _ => Array.empty
      }
      //see SLS[6.16]
      case cond: ScIfStmt if cond.condition.getOrElse(null: ScExpression) == expr => Array((types.Boolean, None))
      case cond: ScIfStmt if cond.elseBranch != None => cond.expectedTypesEx(true)
      //see SLA[6.22]
      case tb: ScTryBlock => tb.lastExpr match {
        case Some(e) if e == expr => tb.getContext.asInstanceOf[ScTryStmt].expectedTypesEx(true)
        case _ => Array.empty
      }
      case fb: ScFinallyBlock => Array((types.Unit, None))
      case te: ScThrowStmt =>
        // Not in the SLS, but in the implementation.
        val throwableClass = JavaPsiFacade.getInstance(te.getProject).findClass("java.lang.Throwable", te.getResolveScope)
        val throwableType = if (throwableClass != null) new ScDesignatorType(throwableClass) else Any
        Array((throwableType, None))
      //see SLS[8.4]
      case c: ScCaseClause => c.getContext.getContext match {
        case m: ScMatchStmt => m.expectedTypesEx(true)
        case b: ScBlockExpr if b.isAnonymousFunction => {
          b.expectedTypesEx(true).flatMap(tp => ScType.extractFunctionType(tp._1) match {
            case Some(ScFunctionType(retType, _)) => Array[(ScType, Option[ScTypeElement])]((retType, None))
            case _ => ScType.extractPartialFunctionType(tp._1) match {
              case Some((des, param, ret)) => Array[(ScType, Option[ScTypeElement])]((ret, None))
              case None => Array[(ScType, Option[ScTypeElement])]()
            }
          })
        }
        case cb: ScCatchBlock =>
          cb.getContext.asInstanceOf[ScTryStmt].expectedTypesEx(true)
        case _ => Array.empty
      }
      //see SLS[6.23]
      case f: ScFunctionExpr => f.expectedTypesEx(true).flatMap(tp => ScType.extractFunctionType(tp._1) match {
        case Some(ScFunctionType(retType, _)) => Array[(ScType, Option[ScTypeElement])]((retType, None))
        case _ => Array[(ScType, Option[ScTypeElement])]()
      })
      case t: ScTypedStmt if t.getLastChild.isInstanceOf[ScSequenceArg] => {
        t.expectedTypesEx(true)
      }
      //SLS[6.13]
      case t: ScTypedStmt => {
        t.typeElement match {
          case Some(te) => Array((te.getType(TypingContext.empty).getOrAny, Some(te)))
          case _ => Array.empty
        }
      }
      //SLS[6.15]
      case a: ScAssignStmt if a.getRExpression.getOrElse(null: ScExpression) == expr => {
        a.getLExpression match {
          case ref: ScReferenceExpression if !a.getParent.isInstanceOf[ScArgumentExprList] ||
                  expr.isInstanceOf[ScUnderscoreSection] /* See SCL-3512, TODO SCL-3525 */ => {
            ref.bind() match {
              case Some(ScalaResolveResult(named: PsiNamedElement, subst: ScSubstitutor)) => {
                ScalaPsiUtil.nameContext(named) match {
                  case v: ScValue =>
                    Array((subst.subst(named.asInstanceOf[ScTypedDefinition].
                      getType(TypingContext.empty).getOrAny), v.typeElement))
                  case v: ScVariable =>
                    Array((subst.subst(named.asInstanceOf[ScTypedDefinition].
                      getType(TypingContext.empty).getOrAny), v.typeElement))
                  case f: ScFunction => Array.empty //todo: find functionName_= method and do as argument call expected type
                  case p: ScParameter => {
                    //for named parameters
                    Array((subst.subst(p.getType(TypingContext.empty).getOrAny), p.typeElement))
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
        val actExpr = expr.getDeepSameElementInContext
        val i = if (actExpr == null) 0 else exprs.findIndexOf(_ == actExpr)
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
        res.map(typeToPair).toArray
      }
      case tuple: ScTuple => {
        val buffer = new ArrayBuffer[ScType]
        val exprs = tuple.exprs
        val actExpr = expr.getDeepSameElementInContext
        val index = exprs.indexOf(actExpr)
        for (tp: ScType <- tuple.expectedTypes(true)) {
          tp match {
            case ScTupleType(comps) if comps.length == tuple.exprs.length => {
              buffer += comps(index)
            }
            case _ =>
          }
        }
        buffer.map(typeToPair).toArray
      }
      case infix: ScInfixExpr if ((infix.isLeftAssoc && infix.lOp == expr) ||
              (!infix.isLeftAssoc && infix.rOp == expr)) && !expr.isInstanceOf[ScTuple] => {
        val res = new ArrayBuffer[ScType]
        val zExpr: ScExpression = expr match {
          case p: ScParenthesisedExpr => p.expr.getOrElse(return Array.empty)
          case _ => expr
        }
        val op = infix.operation
        val tps = if (!withResolvedFunction) op.shapeMultiType else op.multiType
        tps.foreach(processArgsExpected(res, zExpr, 0, _, Seq(zExpr)))
        res.map(typeToPair).toArray
      }
      //SLS[4.1]
      case v: ScPatternDefinition if v.expr == expr.getSameElementInContext => {
        v.typeElement match {
          case Some(te) => Array((v.getType(TypingContext.empty).getOrAny, Some(te)))
          case _ => Array.empty
        }
      }
      case v: ScVariableDefinition if v.expr == expr.getSameElementInContext => {
        v.typeElement match {
          case Some(te) => Array((v.getType(TypingContext.empty).getOrAny, Some(te)))
          case _ => Array.empty
        }
      }
      //SLS[4.6]
      case v: ScFunctionDefinition if (v.body match {
        case None => false
        case Some(b) => b == expr.getSameElementInContext
      }) => {
        v.returnTypeElement match {
          case Some(te) => v.returnType.toOption.map(x => (x, Some(te))).toArray
          case _ => v.getInheritedReturnType.map(typeToPair).toArray
        }
      }
      //default parameters
      case param: ScParameter => {
        param.typeElement match {
          case Some(_) => Array((param.getType(TypingContext.empty).getOrAny, param.typeElement))
          case _ => Array.empty
        }
      }
      case ret: ScReturnStmt => {
        val fun: ScFunction = PsiTreeUtil.getContextOfType(ret, true, classOf[ScFunction])
        if (fun == null) return Array.empty
        fun.returnTypeElement match {
          case Some(rte: ScTypeElement) => {
            fun.returnType match {
              case Success(rt: ScType, _) => Array((rt, Some(rte)))
              case _ => Array.empty
            }
          }
          case None => Array.empty
        }
      }
      case args: ScArgumentExprList => {
        val res = new ArrayBuffer[ScType]
        val exprs: Seq[ScExpression] = args.exprs
        val actExpr = expr.getDeepSameElementInContext
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
          val callOption = args.getParent match {
            case call: ScMethodCall => Some(call)
            case _ => None
          }
          callOption.foreach(call => tps = tps.map(call.updateAccordingToExpectedType(_)))
          tps.foreach(processArgsExpected(res, expr, i, _, exprs, callOption))
        } else {
          //it's constructor
          args.getContext match {
            case constr: ScConstructor => {
              val j = constr.arguments.indexOf(args)
              if (!withResolvedFunction) constr.shapeMultiType(j).foreach(processArgsExpected(res, expr, i, _, exprs))
              else constr.multiType(j).foreach(processArgsExpected(res, expr, i, _, exprs))
            }
            case s: ScSelfInvocation => {
              val j = s.arguments.indexOf(args)
              if (!withResolvedFunction) s.shapeMultiType(j).foreach(processArgsExpected(res, expr, i, _, exprs))
              else s.multiType(j).foreach(processArgsExpected(res, expr, i, _, exprs))
            }
            case _ =>
          }
        }
        res.map(typeToPair).toArray
      }
      case b: ScBlock if b.getContext.isInstanceOf[ScTryBlock]
              || b.getContext.getContext.getContext.isInstanceOf[ScCatchBlock]
              || b.getContext.isInstanceOf[ScCaseClause] 
              || b.getContext.isInstanceOf[ScFunctionExpr] => b.lastExpr match {
        case Some(e) if e == expr => b.expectedTypesEx(true)
        case _ => Array.empty
      }
      case _ => Array.empty
    }

    if (fromUnderscore && ScUnderScoreSectionUtil.underscores(expr).length != 0) {
      val res = new ArrayBuffer[(ScType, Option[ScTypeElement])]
      for (tp <- result) {
        ScType.extractFunctionType(tp._1) match {
          case Some(ScFunctionType(rt: ScType, _)) => res += rt
          case None =>
        }
      }
      res.toArray
    } else result
  }

  private def processArgsExpected(res: ArrayBuffer[ScType], expr: ScExpression, i: Int, tp: TypeResult[ScType],
                                  exprs: Seq[ScExpression], call: Option[ScMethodCall] = None, 
                                  forApply: Boolean = false) {
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
      } else if (expr.isInstanceOf[ScTypedStmt] && expr.asInstanceOf[ScTypedStmt].isSequenceArg && params.length > 0) {
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
            case ScTupleType(args) => applyForParams(args.map(new Parameter("", _, false, false, false)))
            case p: ScParameterizedType if p.getTupleType != None =>
              applyForParams(p.getTupleType.get.components.map(new Parameter("", _, false, false, false)))
            case _ =>
          }
        } else applyForParams(params)
      }
      case Success(t@ScTypePolymorphicType(ScMethodType(_, params, _), typeParams), _) => {
        val subst = t.abstractTypeSubstitutor
        val newParams = params.map(p => p.copy(paramType = subst.subst(p.paramType)))
        if (newParams.length == 1 && !newParams.apply(0).isRepeated && exprs.length > 1) {
          newParams.apply(0).paramType match {
            case ScTupleType(args) => applyForParams(args.map(new Parameter("", _, false, false, false)))
            case p: ScParameterizedType if p.getTupleType != None =>
              applyForParams(p.getTupleType.get.components.map(new Parameter("", _, false, false, false)))
            case _ =>
          }
        } else applyForParams(newParams)
      }
      case Success(t@ScTypePolymorphicType(anotherType, typeParams), _) if !forApply => {
        import Expression._
        val applyProc =
          new MethodResolveProcessor(expr, "apply", List(exprs), Seq.empty, Seq.empty /* todo: ? */,
            StdKinds.methodsOnly, isShapeResolve = true)
        applyProc.processType(anotherType, expr)
        var cand = applyProc.candidates
        if (cand.length == 0 && call != None && !tp.isEmpty) {
          val expr = call.get.getEffectiveInvokedExpr
          //should think about implicit conversions
          for ((t, implicitFunction, importsUsed) <- expr.implicitMap(exprType = Some(tp.get))) {
            var state = ResolveState.initial.put(CachesUtil.IMPLICIT_FUNCTION, implicitFunction)
            expr.getClazzForType(t) match {
              case Some(cl: PsiClass) => state = state.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, cl)
              case _ =>
            }
            applyProc.processType(t, expr, state)
          }
          cand = applyProc.candidates
        }
        if (cand.length == 1) {
          cand(0) match {
            case ScalaResolveResult(fun: ScFunction, s) => {
              var polyType: TypeResult[ScType] = Success(s.subst(fun.polymorphicType) match {
                case ScTypePolymorphicType(internal, params) =>
                  ScTypePolymorphicType(internal, params ++ typeParams)
                case tp => ScTypePolymorphicType(tp, typeParams)
              }, Some(expr))
              call.foreach(call => polyType = call.updateAccordingToExpectedType(polyType))
              processArgsExpected(res, expr, i, polyType, exprs, forApply = true)
            }
            case _ =>
          }
        }
      }
      case Success(anotherType, _) if !forApply => {
        import Expression._
        val applyProc =
          new MethodResolveProcessor(expr, "apply", List(exprs), Seq.empty, Seq.empty /* todo: ? */,
            StdKinds.methodsOnly, isShapeResolve = true)
        applyProc.processType(anotherType, expr)
        var cand = applyProc.candidates
        if (cand.length == 0 && call != None && !tp.isEmpty) {
          val expr = call.get.getEffectiveInvokedExpr
          //should think about implicit conversions
          for ((t, implicitFunction, importsUsed) <- expr.implicitMap(exprType = Some(tp.get))) {
            var state = ResolveState.initial.put(CachesUtil.IMPLICIT_FUNCTION, implicitFunction)
            expr.getClazzForType(t) match {
              case Some(cl: PsiClass) => state = state.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, cl)
              case _ =>
            }
            applyProc.processType(t, expr, state)
          }
          cand = applyProc.candidates
        }
        if (cand.length == 1) {
          cand(0) match {
            case ScalaResolveResult(fun: ScFunction, subst) =>
              var polyType: TypeResult[ScType] = Success(subst.subst(fun.polymorphicType), Some(expr))
              call.foreach(call => polyType = call.updateAccordingToExpectedType(polyType))
              processArgsExpected(res, expr, i, polyType, exprs, forApply = true)
            case _ =>
          }
        }
      }
      case _ =>
    }
  }
}