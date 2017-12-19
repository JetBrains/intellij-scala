package org.jetbrains.plugins.scala.lang.psi.impl.expr

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiTypeExt, SeqExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSequenceArg, ScTupleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ExpectedTypesImpl.TypeResultEx
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{api, _}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor._

/**
 * @author ilyas
 *
 * Utility class to calculate expected type of any expression
 */

class ExpectedTypesImpl extends ExpectedTypes {
  /**
   * Do not use this method inside of resolve or type inference.
   * Using this leads to SOE.
   */
  def smartExpectedType(expr: ScExpression, fromUnderscore: Boolean = true): Option[ScType] =
    smartExpectedTypeEx(expr, fromUnderscore).map(_._1)

  def smartExpectedTypeEx(expr: ScExpression, fromUnderscore: Boolean = true): Option[(ScType, Option[ScTypeElement])] = {
    val types = expectedExprTypes(expr, withResolvedFunction = true, fromUnderscore = fromUnderscore)

    onlyOne(types)
  }

  def expectedExprType(expr: ScExpression, fromUnderscore: Boolean = true): Option[(ScType, Option[ScTypeElement])] = {
    val types = expr.expectedTypesEx(fromUnderscore)

    onlyOne(types)
  }

  private def onlyOne(types: Seq[(ScType, Option[ScTypeElement])]): Option[(ScType, Option[ScTypeElement])] = {
    val distinct =
      types.sortBy {
        case (_: ScAbstractType, _) => 1
        case _ => 0
      }.distinctBy {
        case (ScAbstractType(_, lower, upper), _) if lower == upper => lower
        case (t, _) => t
      }
    distinct match {
      case Seq(tp) => Some(tp)
      case _ => None
    }
  }

  /**
   * @return (expectedType, expectedTypeElement)
   */
  def expectedExprTypes(expr: ScExpression, withResolvedFunction: Boolean = false,
                        fromUnderscore: Boolean = true): Array[(ScType, Option[ScTypeElement])] = {
    import expr.projectContext
    @tailrec
    def fromFunction(tp: (ScType, Option[ScTypeElement])): Array[(ScType, Option[ScTypeElement])] = {
      tp._1 match {
        case FunctionType(retType, _) => Array[(ScType, Option[ScTypeElement])]((retType, None))
        case PartialFunctionType(retType, _) => Array[(ScType, Option[ScTypeElement])]((retType, None))
        case ScAbstractType(_, _, upper) => fromFunction(upper, tp._2)
        case samType if ScalaPsiUtil.isSAMEnabled(expr) =>
          ScalaPsiUtil.toSAMType(samType, expr) match {
            case Some(methodType) => fromFunction(methodType, tp._2)
            case _ => Array[(ScType, Option[ScTypeElement])]()
          }
        case _ => Array[(ScType, Option[ScTypeElement])]()
      }
    }

    def mapResolves(resolves: Array[ResolveResult], types: Array[TypeResult]): Array[(TypeResult, Boolean)] = {
      resolves.zip(types).map {
        case (r: ScalaResolveResult, tp) =>
          (tp, isApplyDynamicNamed(r))
        case (_, tp) => (tp, false)
      }
    }

    val sameInContext = expr.getSameElementInContext

    val result: Array[(ScType, Option[ScTypeElement])] = expr.getContext match {
      case p: ScParenthesisedExpr => p.expectedTypesEx(fromUnderscore = false)
      //see SLS[6.11]
      case b: ScBlockExpr => b.lastExpr match {
        case Some(e) if b.needCheckExpectedType && e == sameInContext => b.expectedTypesEx(fromUnderscore = true)
        case _ => Array.empty
      }
      //see SLS[6.16]
      case cond: ScIfStmt if cond.condition.getOrElse(null: ScExpression) == sameInContext => Array((api.Boolean, None))
      case cond: ScIfStmt if cond.elseBranch.isDefined => cond.expectedTypesEx(fromUnderscore = true)
      //see SLA[6.22]
      case tb: ScTryBlock => tb.lastExpr match {
        case Some(e) if e == expr => tb.getContext.asInstanceOf[ScTryStmt].expectedTypesEx(fromUnderscore = true)
        case _ => Array.empty
      }
      case wh: ScWhileStmt if wh.condition.getOrElse(null: ScExpression) == sameInContext => Array((api.Boolean, None))
      case _: ScWhileStmt => Array((Unit, None))
      case d: ScDoStmt if d.condition.getOrElse(null: ScExpression) == sameInContext => Array((api.Boolean, None))
      case _: ScDoStmt => Array((api.Unit, None))
      case _: ScFinallyBlock => Array((api.Unit, None))
      case _: ScCatchBlock => Array.empty
      case te: ScThrowStmt =>
        // Not in the SLS, but in the implementation.
        val throwableClass = ScalaPsiManager.instance(te.getProject).getCachedClass(te.resolveScope, "java.lang.Throwable")
        val throwableType = throwableClass.map(new ScDesignatorType(_)).getOrElse(Any)
        Array((throwableType, None))
      //see SLS[8.4]
      case c: ScCaseClause => c.getContext.getContext match {
        case m: ScMatchStmt => m.expectedTypesEx(fromUnderscore = true)
        case b: ScBlockExpr if b.isInCatchBlock =>
          b.getContext.getContext.asInstanceOf[ScTryStmt].expectedTypesEx(fromUnderscore = true)
        case b: ScBlockExpr if b.isAnonymousFunction =>
          b.expectedTypesEx(fromUnderscore = true).flatMap(tp => fromFunction(tp))
        case _ => Array.empty
      }
      //see SLS[6.23]
      case f: ScFunctionExpr => f.expectedTypesEx(fromUnderscore = true).flatMap(tp => fromFunction(tp))
      case t: ScTypedStmt if t.getLastChild.isInstanceOf[ScSequenceArg] =>
        t.expectedTypesEx(fromUnderscore = true)
      //SLS[6.13]
      case t: ScTypedStmt =>
        t.typeElement match {
          case Some(te) => Array((te.`type`().getOrAny, Some(te)))
          case _ => Array.empty
        }
      //SLS[6.15]
      case a: ScAssignStmt if a.getRExpression.getOrElse(null: ScExpression) == sameInContext =>
        a.getLExpression match {
          case ref: ScReferenceExpression if (!a.getContext.isInstanceOf[ScArgumentExprList] && !(
            a.getContext.isInstanceOf[ScInfixArgumentExpression] && a.getContext.asInstanceOf[ScInfixArgumentExpression].isCall)) ||
                  ref.qualifier.isDefined ||
                  ScUnderScoreSectionUtil.isUnderscore(expr) /* See SCL-3512, SCL-3525, SCL-4809, SCL-6785 */ =>
            ref.bind() match {
              case Some(ScalaResolveResult(named: PsiNamedElement, subst: ScSubstitutor)) =>
                ScalaPsiUtil.nameContext(named) match {
                  case v: ScValue =>
                    Array((subst.subst(named.asInstanceOf[ScTypedDefinition].
                      `type`().getOrAny), v.typeElement))
                  case v: ScVariable =>
                    Array((subst.subst(named.asInstanceOf[ScTypedDefinition].
                      `type`().getOrAny), v.typeElement))
                  case f: ScFunction if f.paramClauses.clauses.isEmpty =>
                    a.mirrorMethodCall match {
                      case Some(call) =>
                        call.args.exprs.head.expectedTypesEx(fromUnderscore = fromUnderscore)
                      case None => Array.empty
                    }
                  case p: ScParameter =>
                    //for named parameters
                    Array((subst.subst(p.`type`().getOrAny), p.typeElement))
                  case f: PsiField =>
                    Array((subst.subst(f.getType.toScType()), None))
                  case _ => Array.empty
                }
              case _ => Array.empty
            }
          case _: ScReferenceExpression => expectedExprTypes(a)
          case _: ScMethodCall =>
            a.mirrorMethodCall match {
              case Some(mirrorCall) => mirrorCall.args.exprs.last.expectedTypesEx(fromUnderscore = fromUnderscore)
              case _ => Array.empty
            }
          case _ => Array.empty
        }
      //method application
      case tuple: ScTuple if tuple.isCall =>
        val res = new ArrayBuffer[(ScType, Option[ScTypeElement])]
        val exprs: Seq[ScExpression] = tuple.exprs
        val actExpr = expr.getDeepSameElementInContext
        val i = if (actExpr == null) 0 else exprs.indexWhere(_ == actExpr)
        val callExpression = tuple.getContext.asInstanceOf[ScInfixExpr].operation
        if (callExpression != null) {
          val tps = callExpression match {
            case ref: ScReferenceExpression =>
              if (!withResolvedFunction) mapResolves(ref.shapeResolve, ref.shapeMultiType)
              else mapResolves(ref.multiResolve(false), ref.multiType)
            case _ => Array((callExpression.getNonValueType(), false))
          }
          tps.foreach { case (r, isDynamicNamed) =>
            processArgsExpected(res, expr, i, r, exprs, isDynamicNamed = isDynamicNamed)
          }
        }
        res.toArray
      case tuple: ScTuple =>
        val buffer = new ArrayBuffer[(ScType, Option[ScTypeElement])]
        val exprs = tuple.exprs
        val actExpr = expr.getDeepSameElementInContext
        val index = exprs.indexOf(actExpr)
        @tailrec
        def addType(aType: ScType): Unit = {
          aType match {
            case _: ScAbstractType => addType(aType.removeAbstracts)
            case TupleType(comps) if comps.length == exprs.length =>
              buffer += ((comps(index), None))
            case _ =>
          }
        }
        if (index >= 0) {
          for (tp: ScType <- tuple.expectedTypes(fromUnderscore = true)) addType(tp)
        }
        buffer.toArray
      case infix: ScInfixExpr if infix.getArgExpr == sameInContext && !expr.isInstanceOf[ScTuple] =>
        val res = new ArrayBuffer[(ScType, Option[ScTypeElement])]
        val zExpr: ScExpression = expr match {
          case p: ScParenthesisedExpr => p.expr.getOrElse(return Array.empty)
          case _ => expr
        }
        val op = infix.operation
        var tps =
          if (!withResolvedFunction) mapResolves(op.shapeResolve, op.shapeMultiType)
          else mapResolves(op.multiResolve(false), op.multiType)
        tps = tps.map { case (tp, isDynamicNamed) =>
          (tp.updateAccordingToExpectedType(infix), isDynamicNamed)
        }
        tps.foreach { case (tp, isDynamicNamed) =>
            processArgsExpected(res, zExpr, 0, tp, Seq(zExpr), Some(infix), isDynamicNamed = isDynamicNamed)
        }
        res.toArray
      //SLS[4.1]
      case v @ ScPatternDefinition.expr(expr) if expr == sameInContext =>
        v.typeElement match {
          case Some(te) => Array((v.`type`().getOrAny, Some(te)))
          case _ => Array.empty
        }
      case v @ ScVariableDefinition.expr(expr) if expr == sameInContext =>
        v.typeElement match {
          case Some(te) => Array((v.`type`().getOrAny, Some(te)))
          case _ => Array.empty
        }
      //SLS[4.6]
      case v: ScFunctionDefinition if (v.body match {
        case None => false
        case Some(b) => b == sameInContext
      }) =>
        v.returnTypeElement match {
          case Some(te) => v.returnType.toOption.map(x => (x, Some(te))).toArray
          case None if !v.hasAssign => Array((api.Unit, None))
          case _ => v.getInheritedReturnType.map((_, None)).toArray
        }
      //default parameters
      case param: ScParameter =>
        param.typeElement match {
          case Some(_) => Array((param.`type`().getOrAny, param.typeElement))
          case _ => Array.empty
        }
      case ret: ScReturnStmt =>
        val fun: ScFunction = PsiTreeUtil.getContextOfType(ret, true, classOf[ScFunction])
        if (fun == null) return Array.empty
        fun.returnTypeElement match {
          case Some(rte: ScTypeElement) =>
            fun.returnType match {
              case Right(rt) => Array((rt, Some(rte)))
              case _ => Array.empty
            }
          case None => Array.empty
        }
      case args: ScArgumentExprList =>
        val res = new ArrayBuffer[(ScType, Option[ScTypeElement])]
        val exprs: Seq[ScExpression] = args.exprs
        val actExpr = expr.getDeepSameElementInContext
        val i = if (actExpr == null) 0 else {
          val r = exprs.indexWhere(_ == actExpr)
          if (r == -1) 0 else r
        }
        val callExpression = args.callExpression
        if (callExpression != null) {
          var tps: Array[(TypeResult, Boolean)] = callExpression match {
            case ref: ScReferenceExpression =>
              if (!withResolvedFunction) mapResolves(ref.shapeResolve, ref.shapeMultiType)
              else mapResolves(ref.multiResolve(false), ref.multiType)
            case gen: ScGenericCall =>
              if (!withResolvedFunction) {
                val multiType = gen.shapeMultiType
                gen.shapeMultiResolve.map(mapResolves(_, multiType)).getOrElse(multiType.map((_, false)))
              } else {
                val multiType = gen.multiType
                gen.multiResolve.map(mapResolves(_, multiType)).getOrElse(multiType.map((_, false)))
              }
            case _ => Array((callExpression.getNonValueType(), false))
          }
          val callOption = args.getParent match {
            case call: MethodInvocation => Some(call)
            case _ => None
          }
          callOption.foreach(call => tps = tps.map { case (r, isDynamicNamed) =>
            (r.updateAccordingToExpectedType(call), isDynamicNamed)
          })
          tps.filterNot(_._1.exists(_.equiv(Nothing)))foreach { case (r, isDynamicNamed) =>
            processArgsExpected(res, expr, i, r, exprs, callOption, isDynamicNamed = isDynamicNamed)
          }
        } else {
          //it's constructor
          args.getContext match {
            case constr: ScConstructor =>
              val j = constr.arguments.indexOf(args)
              val tps =
                if (!withResolvedFunction) constr.shapeMultiType(j)
                else constr.multiType(j)
              tps.foreach(processArgsExpected(res, expr, i, _, exprs))
            case s: ScSelfInvocation =>
              val j = s.arguments.indexOf(args)
              if (!withResolvedFunction) s.shapeMultiType(j).foreach(processArgsExpected(res, expr, i, _, exprs))
              else s.multiType(j).foreach(processArgsExpected(res, expr, i, _, exprs))
            case _ =>
          }
        }
        res.toArray
      case b: ScBlock if b.getContext.isInstanceOf[ScTryBlock]
              || b.getContext.getContext.getContext.isInstanceOf[ScCatchBlock]
              || b.getContext.isInstanceOf[ScCaseClause]
              || b.getContext.isInstanceOf[ScFunctionExpr] => b.lastExpr match {
        case Some(e) if sameInContext == e => b.expectedTypesEx(fromUnderscore = true)
        case _ => Array.empty
      }
      case _ => Array.empty
    }

    @tailrec
    def checkIsUnderscore(expr: ScExpression): Boolean = {
      expr match {
        case p: ScParenthesisedExpr =>
          p.expr match {
            case Some(e) => checkIsUnderscore(e)
            case _ => false
          }
        case _ => ScUnderScoreSectionUtil.underscores(expr).nonEmpty
      }
    }

    if (fromUnderscore && checkIsUnderscore(expr)) {
      val res = new ArrayBuffer[(ScType, Option[ScTypeElement])]
      for (tp <- result) {
        tp._1 match {
          case FunctionType(rt: ScType, _) => res += ((rt, None))
          case _ =>
        }
      }
      res.toArray
    } else result
  }

  @tailrec
  private def processArgsExpected(res: ArrayBuffer[(ScType, Option[ScTypeElement])], expr: ScExpression, i: Int,
                                  tp: TypeResult, exprs: Seq[ScExpression], call: Option[MethodInvocation] = None,
                                  forApply: Boolean = false, isDynamicNamed: Boolean = false) {
    import expr.projectContext

    def applyForParams(params: Seq[Parameter]) {
      val p: (ScType, Option[ScTypeElement]) =
        if (i >= params.length && params.nonEmpty && params.last.isRepeated)
          (params.last.paramType, params.last.paramInCode.flatMap(_.typeElement))
        else if (i >= params.length) (Nothing, None)
        else (params(i).paramType, params(i).paramInCode.flatMap(_.typeElement))
      expr match {
        case assign: ScAssignStmt =>
          if (isDynamicNamed) {
            val (tp, te) = p
            tp.removeAbstracts match {
              case TupleType(comps) if comps.length == 2 =>
                res += ((comps(1), te.map {
                  case t: ScTupleTypeElement if t.components.length == 2 => t.components(1)
                  case t => t
                }))
              case _ => res += p
            }
          } else {
            val lE = assign.getLExpression
            lE match {
              case ref: ScReferenceExpression if ref.qualifier.isEmpty =>
                params.find(parameter => ScalaNamesUtil.equivalent(parameter.name, ref.refName)) match {
                  case Some(param) => res += ((param.paramType, param.paramInCode.flatMap(_.typeElement)))
                  case _ => res += p
                }
              case _ => res += p
            }
          }
        case typedStmt: ScTypedStmt if typedStmt.isSequenceArg && params.nonEmpty =>
          val seqClass: Array[PsiClass] = ScalaPsiManager.instance.
                  getCachedClasses(expr.resolveScope, "scala.collection.Seq").filter(!_.isInstanceOf[ScObject])
          if (seqClass.length != 0) {
            val tp = ScParameterizedType(ScalaType.designator(seqClass(0)), Seq(params.last.paramType))
            res += ((tp, None))
          }
        case _ => res += p
      }
    }
    tp match {
      case Right(ScMethodType(_, params, _)) =>
        if (params.length == 1 && !params.head.isRepeated && exprs.length > 1) {
          params.head.paramType.removeAbstracts match {
            case TupleType(args) => applyForParams(args.zipWithIndex.map {
              case (tpe, index) => Parameter(tpe, isRepeated = false, index = index)
            })
            case _ =>
          }
        } else applyForParams(params)
      case Right(t@ScTypePolymorphicType(ScMethodType(_, params, _), _)) =>
        val subst = t.abstractTypeSubstitutor
        val newParams = params.map(p => p.copy(paramType = subst.subst(p.paramType)))
        if (newParams.length == 1 && !newParams.head.isRepeated && exprs.length > 1) {
          newParams.head.paramType.removeAbstracts match {
            case TupleType(args) => applyForParams(args.zipWithIndex.map {
              case (tpe, index) => Parameter(tpe, isRepeated = false, index = index)
            })
            case _ =>
          }
        } else applyForParams(newParams)
      case Right(ScTypePolymorphicType(anotherType, typeParams)) if !forApply =>
        val cand = call.getOrElse(expr).applyShapeResolveForExpectedType(anotherType, exprs, call)
        if (cand.length == 1) {
          cand(0) match {
            case r@ScalaResolveResult(fun: ScFunction, s) =>
              def update(tp: ScType): ScType = {
                if (r.isDynamic) getDynamicReturn(tp)
                else tp
              }

              var polyType: TypeResult = Right(s.subst(fun.polymorphicType()) match {
                case ScTypePolymorphicType(internal, params) =>
                  update(ScTypePolymorphicType(internal, params ++ typeParams))
                case tp => update(ScTypePolymorphicType(tp, typeParams))
              })
              call.foreach(call => polyType = polyType.updateAccordingToExpectedType(call))
              processArgsExpected(res, expr, i, polyType, exprs, forApply = true, isDynamicNamed = isApplyDynamicNamed(r))
            case _ =>
          }
        }
      case Right(anotherType) if !forApply =>
        val cand = call.getOrElse(expr).applyShapeResolveForExpectedType(anotherType, exprs, call)
        if (cand.length == 1) {
          cand(0) match {
            case r@ScalaResolveResult(fun: ScFunction, subst) =>
              def update(tp: ScType): ScType = {
                if (r.isDynamic) getDynamicReturn(tp)
                else tp
              }

              var polyType: TypeResult = Right(update(subst.subst(fun.polymorphicType())))
              call.foreach(call => polyType = polyType.updateAccordingToExpectedType(call))
              processArgsExpected(res, expr, i, polyType, exprs, forApply = true, isDynamicNamed = isApplyDynamicNamed(r))
            case _ =>
          }
        }
      case _ =>
    }
  }
}

object ExpectedTypesImpl {
  implicit class TypeResultEx(val tr: TypeResult) extends AnyVal {
    /**
      * This method useful in case if you want to update some polymorphic type
      * according to method call expected type
      */
    def updateAccordingToExpectedType(call: MethodInvocation, canThrowSCE: Boolean = false): TypeResult = {
      InferUtil.updateAccordingToExpectedType(tr, fromImplicitParameters = false, filterTypeParams = false,
        expectedType = call.expectedType(), expr = call, canThrowSCE)
    }
  }
}
