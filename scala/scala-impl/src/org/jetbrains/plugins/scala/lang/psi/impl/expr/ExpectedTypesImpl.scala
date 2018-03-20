package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, PsiTypeExt, SeqExt}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSequenceArg, ScTupleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ExpectedTypes._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ExpectedTypesImpl._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{api, _}
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor._
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

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

  def smartExpectedTypeEx(expr: ScExpression, fromUnderscore: Boolean = true): Option[ParameterType] = {
    val types = expectedExprTypes(expr, withResolvedFunction = true, fromUnderscore = fromUnderscore)

    onlyOne(types)
  }

  def expectedExprType(expr: ScExpression, fromUnderscore: Boolean = true): Option[ParameterType] = {
    val types = expr.expectedTypesEx(fromUnderscore)

    onlyOne(types)
  }

  private def onlyOne(types: Seq[ParameterType]): Option[ParameterType] = {
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
                        fromUnderscore: Boolean = true): Array[ParameterType] = {
    import expr.projectContext
    @tailrec
    def fromFunction(tp: ParameterType): Array[ParameterType] = {
      tp._1 match {
        case FunctionType(retType, _) => Array((retType, None))
        case PartialFunctionType(retType, _) => Array((retType, None))
        case ScAbstractType(_, _, upper) => fromFunction(upper, tp._2)
        case samType if ScalaPsiUtil.isSAMEnabled(expr) =>
          ScalaPsiUtil.toSAMType(samType, expr) match {
            case Some(methodType) => fromFunction(methodType, tp._2)
            case _ => Array.empty
          }
        case _ => Array.empty
      }
    }

    def mapResolves(resolves: Array[ScalaResolveResult], types: Array[TypeResult]): Array[(TypeResult, Boolean)] = {
      resolves.zip(types).map {
        case (r, tp) =>
          (tp, isApplyDynamicNamed(r))
        case (_, tp) => (tp, false)
      }
    }

    val sameInContext = expr.getDeepSameElementInContext

    val result: Array[ParameterType] = expr.getContext match {
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
        val res = new ArrayBuffer[ParameterType]
        val exprs: Seq[ScExpression] = tuple.exprs
        val i = if (sameInContext == null) 0 else exprs.indexWhere(_ == sameInContext)
        val callExpression = tuple.getContext.asInstanceOf[ScInfixExpr].operation
        if (callExpression != null) {
          val tps = callExpression match {
            case ref: ScReferenceExpression =>
              if (!withResolvedFunction) mapResolves(ref.shapeResolve, ref.shapeMultiType)
              else mapResolves(ref.multiResolveScala(false), ref.multiType)
            case _ => Array((callExpression.getNonValueType(), false))
          }
          tps.foreach { case (r, isDynamicNamed) =>
            processArgsExpected(res, expr, r, exprs, i, isDynamicNamed = isDynamicNamed)
          }
        }
        res.toArray
      case tuple: ScTuple =>
        val buffer = new ArrayBuffer[ParameterType]
        val exprs = tuple.exprs
        val index = exprs.indexOf(sameInContext)
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
      case infix@ScInfixExpr.withAssoc(_, operation, `sameInContext`) if !expr.isInstanceOf[ScTuple] =>
        val zExpr: ScExpression = expr match {
          case p: ScParenthesisedExpr => p.innerElement.getOrElse(return Array.empty)
          case _ => expr
        }
        val tps =
          if (withResolvedFunction) mapResolves(operation.multiResolveScala(false), operation.multiType)
          else mapResolves(operation.shapeResolve, operation.shapeMultiType)

        val updated = tps.map { case (tp, isDynamicNamed) =>
          (tp.map(infix.updateAccordingToExpectedType), isDynamicNamed)
        }

        val res = new ArrayBuffer[ParameterType]
        updated.foreach { case (tp, isDynamicNamed) =>
            processArgsExpected(res, zExpr, tp, Seq(zExpr), 0, Some(infix), isDynamicNamed = isDynamicNamed)
        }
        res.toArray
      //SLS[4.1]
      case v @ ScPatternDefinition.expr(`sameInContext`)  => declaredOrInheritedType(v)
      case v @ ScVariableDefinition.expr(`sameInContext`) => declaredOrInheritedType(v)
      //SLS[4.6]
      case v: ScFunctionDefinition if v.body.contains(sameInContext) => declaredOrInheritedType(v)
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
        val res = new ArrayBuffer[ParameterType]
        val exprs = args.exprs
        val i = if (sameInContext == null) 0 else {
          val r = exprs.indexWhere(_ == sameInContext)
          if (r == -1) 0 else r
        }
        val callExpression = args.callExpression
        if (callExpression != null) {
          var tps = callExpression match {
            case ref: ScReferenceExpression =>
              if (!withResolvedFunction) mapResolves(ref.shapeResolve, ref.shapeMultiType)
              else mapResolves(ref.multiResolveScala(false), ref.multiType)
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
            (r.map(call.updateAccordingToExpectedType), isDynamicNamed)
          })
          tps.filterNot(_._1.exists(_.equiv(Nothing)))foreach { case (r, isDynamicNamed) =>
            processArgsExpected(res, expr, r, exprs, i, callOption, isDynamicNamed = isDynamicNamed)
          }
        } else {
          //it's constructor
          args.getContext match {
            case constr: ScConstructor =>
              val j = constr.arguments.indexOf(args)
              val tps =
                if (!withResolvedFunction) constr.shapeMultiType(j)
                else constr.multiType(j)
              tps.foreach((invokedExprType: TypeResult) => processArgsExpected(res, expr, invokedExprType, exprs, i))
            case s: ScSelfInvocation =>
              val j = s.arguments.indexOf(args)
              if (!withResolvedFunction) s.shapeMultiType(j).foreach((invokedExprType: TypeResult) => processArgsExpected(res, expr, invokedExprType, exprs, i))
              else s.multiType(j).foreach((invokedExprType: TypeResult) => processArgsExpected(res, expr, invokedExprType, exprs, i))
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
          p.innerElement match {
            case Some(e) => checkIsUnderscore(e)
            case _ => false
          }
        case _ => ScUnderScoreSectionUtil.underscores(expr).nonEmpty
      }
    }

    if (fromUnderscore && checkIsUnderscore(expr)) {
      val res = new ArrayBuffer[ParameterType]
      for (tp <- result) {
        tp._1 match {
          case FunctionType(rt: ScType, _) => res += ((rt, None))
          case _ =>
        }
      }
      res.toArray
    } else result
  }

  private def computeExpectedParamType(expr: ScExpression,
                                       invokedExprType: TypeResult,
                                       argExprs: Seq[ScExpression],
                                       idx: Int,
                                       call: Option[MethodInvocation] = None,
                                       forApply: Boolean = false,
                                       isDynamicNamed: Boolean = false): Option[ParameterType] = {

    def fromMethodTypeParams(params: Seq[Parameter], subst: ScSubstitutor = ScSubstitutor.empty): Option[ParameterType] = {
      val newParams =
        if (subst.isEmpty) params
        else params.map(p => p.copy(paramType = subst.subst(p.paramType)))

      val autoTupling = newParams.length == 1 && !newParams.head.isRepeated && argExprs.length > 1

      if (autoTupling) {
        newParams.head.paramType.removeAbstracts match {
          case TupleType(args) => paramTypeFromExpr(expr, paramsFromTuple(args), idx, isDynamicNamed)
          case _ => None
        }
      }
      else paramTypeFromExpr(expr, newParams, idx, isDynamicNamed)
    }

    //returns properly substituted method type of `apply` method invocation and whether it's apply dynamic named
    def tryApplyMethod(internalType: ScType, typeParams: Seq[TypeParameter]): Option[(TypeResult, Boolean)] = {
      call.getOrElse(expr).shapeResolveApplyMethod(internalType, argExprs, call) match {
        case Array(r@ScalaResolveResult(fun: ScFunction, s)) =>

          val polyType = fun.polymorphicType(s) match {
            case ScTypePolymorphicType(internal, params) =>
              ScTypePolymorphicType(internal, params ++ typeParams)
            case anotherType if typeParams.nonEmpty => ScTypePolymorphicType(anotherType, typeParams)
            case anotherType => anotherType
          }

          val applyMethodType = polyType
            .updateTypeOfDynamicCall(r.isDynamic)

          val updatedMethodCall = call.map(_.updateAccordingToExpectedType(applyMethodType))
            .getOrElse(applyMethodType)

          Some((Right(updatedMethodCall), isApplyDynamicNamed(r)))
        case _ =>
          None
      }
    }

    invokedExprType match {
      case Right(ScMethodType(_, params, _)) =>
        fromMethodTypeParams(params)
      case Right(t@ScTypePolymorphicType(ScMethodType(_, params, _), _)) =>
        fromMethodTypeParams(params, t.abstractTypeSubstitutor)
      case Right(anotherType) if !forApply =>
        val (internalType, typeParams) = anotherType match {
          case ScTypePolymorphicType(internal, tps) => (internal, tps)
          case t => (t, Seq.empty)
        }
        tryApplyMethod(internalType, typeParams) match {
          case Some((applyInvokedType, isApplyDynamicNamed)) =>
            computeExpectedParamType(expr, applyInvokedType, argExprs, idx, forApply = true, isDynamicNamed = isApplyDynamicNamed)
          case _ => None
        }
      case _ => None
    }
  }

  private def processArgsExpected(res: ArrayBuffer[(ScType, Option[ScTypeElement])],
                                  expr: ScExpression,
                                  invokedExprType: TypeResult,
                                  argExprs: Seq[ScExpression],
                                  idx: Int,
                                  call: Option[MethodInvocation] = None,
                                  forApply: Boolean = false,
                                  isDynamicNamed: Boolean = false): Unit = {

    res ++= computeExpectedParamType(expr, invokedExprType, argExprs, idx, call, forApply, isDynamicNamed)
  }

  private def paramTypeFromExpr(expr: ScExpression, params: Seq[Parameter], idx: Int, isDynamicNamed: Boolean): Option[ParameterType] = {
    import expr.elementScope

    def findByIdx(params: Seq[Parameter]): ParameterType = {
      def simple = (params(idx).paramType, typeElem(params(idx)))
      def repeated = (params.last.paramType, typeElem(params.last))

      if (idx >= params.length)
        if (params.nonEmpty && params.last.isRepeated) repeated
        else (Nothing, None)
      else simple
    }

    expr match {
      case assign: ScAssignStmt => Some {
        if (isDynamicNamed) paramTypeForDynamicNamed(findByIdx(params))
        else paramTypeForNamed(assign, params).getOrElse(findByIdx(params))
      }
      case typedStmt: ScTypedStmt if typedStmt.isSequenceArg && params.nonEmpty =>
        paramTypeForRepeated(params)
      case _ =>
        Some(findByIdx(params))
    }
  }

  private def typeElem(parameter: Parameter): Option[ScTypeElement] = parameter.paramInCode.flatMap(_.typeElement)

  private def paramTypeForDynamicNamed(original: ParameterType): ParameterType = {
    val (tp, te) = original
    tp.removeAbstracts match {
      case TupleType(comps) if comps.length == 2 =>
        val actualArg = (comps(1), te.map {
          case t: ScTupleTypeElement if t.components.length == 2 => t.components(1)
          case t => t
        })
        actualArg
      case _ => (tp, te)
    }
  }

  private def paramTypeForNamed(assign: ScAssignStmt, params: Seq[Parameter]): Option[ParameterType] = {
    val lE = assign.getLExpression
    lE match {
      case ref: ScReferenceExpression if ref.qualifier.isEmpty =>
        params
          .find(parameter => ScalaNamesUtil.equivalent(parameter.name, ref.refName))
          .map (param => (param.paramType, typeElem(param)))
      case _ => None
    }
  }

  private def paramTypeForRepeated(params: Seq[Parameter])(implicit elementScope: ElementScope): Option[ParameterType] = {
    val seqClass = elementScope.getCachedClass("scala.collection.Seq")
    seqClass.map { seq =>
      (ScParameterizedType(ScalaType.designator(seq), Seq(params.last.paramType)), None)
    }
  }

  private def paramsFromTuple(tupleArgs: Seq[ScType]): Seq[Parameter] = tupleArgs.zipWithIndex.map {
    case (tpe, index) => Parameter(tpe, isRepeated = false, index = index)
  }

  private def declaredOrInheritedType(member: ScMember): Array[ParameterType] = {
    import member.projectContext

    val declaredType = member match {
      case fun: ScFunctionDefinition if fun.returnTypeElement.isEmpty && !fun.hasAssign =>
        Some((api.Unit, None))
      case fun: ScFunction =>
        fun.returnTypeElement.flatMap(te => fun.returnType.toOption.map((_, Some(te))))
      case v: ScValueOrVariable =>
        v.typeElement.map(te => (te.`type`().getOrAny, Some(te)))
      case _ => return Array.empty
    }
    declaredType.orElse {
      inheritedType(member).map((_, None))
    }.toArray
  }

  private def inheritedType(member: ScMember): Option[ScType] = {
    import member.projectContext

    //is necessary to avoid recursion
    if (member.getParent.isInstanceOf[ScEarlyDefinitions])
      return None

    val typeParameters =
      member.asOptionOf[ScFunction].map(_.typeParameters).getOrElse(Seq.empty)

    val superMethodAndSubstitutor = member match {
      case fun: ScFunction => fun.superMethodAndSubstitutor
      case other: ScMember => ScalaPsiUtil.valSuperMethodAndSubstitutor(other)
    }
    superMethodAndSubstitutor match {
      case Some((fun: ScFunction, subst)) =>
        val typeParamSubst =
          ScSubstitutor.bind(fun.typeParameters, typeParameters)(TypeParameterType(_))

        fun.returnType.toOption.map(typeParamSubst.followed(subst).subst)
      case Some((fun: ScSyntheticFunction, subst)) =>
        val typeParamSubst =
          ScSubstitutor.bind(fun.typeParameters, typeParameters)(TypeParameterType(_))

        Some(typeParamSubst.subst(fun.retType))
      case Some((fun: PsiMethod, subst)) =>
        val typeParamSubst =
          ScSubstitutor.bind(fun.getTypeParameters, typeParameters)(TypeParameterType(_))

        Some(typeParamSubst.followed(subst).subst(fun.getReturnType.toScType()))
      case _ => None
    }
  }
}

private object ExpectedTypesImpl {

  implicit class ScMethodCallEx(private val invocation: MethodInvocation) extends AnyVal {

    def updateAccordingToExpectedType(`type`: ScType): ScType =
      InferUtil.updateAccordingToExpectedType(`type`, fromImplicitParameters = false, filterTypeParams = false, invocation.expectedType(), invocation, canThrowSCE = false)
  }

  implicit class ScExpressionForExpectedTypesEx(val expr: ScExpression) extends AnyVal {

    import expr.projectContext
    import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression._

    @CachedWithRecursionGuard(expr, Array.empty[ScalaResolveResult], ModCount.getBlockModificationCount)
    def shapeResolveApplyMethod(tp: ScType, exprs: Seq[ScExpression], call: Option[MethodInvocation]): Array[ScalaResolveResult] = {
      val applyProc =
        new MethodResolveProcessor(expr, "apply", List(exprs), Seq.empty, Seq.empty /* todo: ? */ ,
          StdKinds.methodsOnly, isShapeResolve = true)
      applyProc.processType(tp, expr)
      var cand = applyProc.candidates
      if (cand.length == 0 && call.isDefined) {
        val expr = call.get.getEffectiveInvokedExpr
        ScalaPsiUtil.findImplicitConversion(expr, "apply", expr, applyProc, noImplicitsForArgs = false, Some(tp)).foreach { result =>
          val builder = new ImplicitResolveResult.ResolverStateBuilder(result).withImplicitFunction
          applyProc.processType(result.typeWithDependentSubstitutor, expr, builder.state)
          cand = applyProc.candidates
        }
      }
      if (cand.length == 0 && conformsToDynamic(tp, expr.resolveScope) && call.isDefined) {
        cand = ScalaPsiUtil.processTypeForUpdateOrApplyCandidates(call.get, tp, isShape = true, isDynamic = true)
      }
      cand
    }
  }

}
