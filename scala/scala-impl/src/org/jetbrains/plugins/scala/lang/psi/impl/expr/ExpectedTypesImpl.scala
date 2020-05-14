package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, PsiTypeExt, SeqExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses, ScReferencePattern, ScTuplePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSequenceArg, ScTupleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ExpectedTypes._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScUnderScoreSectionUtil, _}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ExpectedTypesImpl._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
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
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveState, StdKinds}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_13

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
  override def smartExpectedType(expr: ScExpression, fromUnderscore: Boolean = true): Option[ScType] =
    smartExpectedTypeEx(expr, fromUnderscore).map(_._1)

  def smartExpectedTypeEx(expr: ScExpression, fromUnderscore: Boolean = true): Option[ParameterType] = {
    val types = expectedExprTypes(expr, withResolvedFunction = true, fromUnderscore = fromUnderscore)

    filterAlternatives(types, expr)
  }

  override def expectedExprType(expr: ScExpression, fromUnderscore: Boolean = true): Option[ParameterType] = {
    val types = expr.expectedTypesEx(fromUnderscore)

    filterAlternatives(types, expr)
  }

  private[this] def filterAlternatives(
    types: Seq[ParameterType],
    place: PsiElement
  ): Option[ParameterType] = {
    val distinct =
      types.sortBy {
        case (_: ScAbstractType, _) => 1
        case _                      => 0
      }.distinctBy {
        case (ScAbstractType(_, lower, upper), _) if lower == upper => lower
        case (t, _)                                                 => t
      }

    if (distinct.isEmpty)   None
    if (distinct.size == 1) distinct.headOption
    else {
      val tpes = distinct.map(_._1)
      expectedFunctionTypeFromOverloadedAlternatives(tpes, place)
    }
  }

  private def onlyOne[T](types: Seq[T]): Option[T] =
    if (types.size == 1) types.headOption
    else                 None

  /** Returns arity of the functional literal `e`, taking tupling into account. */
  private[this] def aritiesOf(e: PsiElement): Arity = {
    import Arity._

    e match {
      case block: ScBlockExpr if block.isAnonymousFunction => aritiesOf(block.caseClauses.get)
      case clauses: ScCaseClauses                          => clauses.caseClause.pattern.fold(NotAFunction : Arity)(aritiesOf)
      case _: ScReferencePattern                           => FunctionLiteral(1)
      case tuple: ScTuplePattern                           => TuplePattern(tuple.subpatterns.size)
      case fn: ScFunctionExpr                              => FunctionLiteral(fn.parameters.size)
      case e: ScExpression =>
        val underscores = ScUnderScoreSectionUtil.underscores(e).size
        if (underscores == 0) NotAFunction
        else                  FunctionLiteral(underscores)
      case _ => NotAFunction
    }
  }

  /**
   * When type checking a function literal supplied to an overloaded method
   * we first filter expected types based on function arity and then if scalaVersion >= 2.13
   * merge function-like types with equivalent parameters (more on that below).
   *
   * See: https://github.com/scala/scala/pull/6871
   * We only provide an expected type (for each argument position) when:
   * - there is at least one FunctionN type expected by one of the overloads:
   *   in this case, the expected type is a FunctionN[Ti, ?], where Ti are the argument types (they must all be =:=),
   *   and the expected result type is elided using a wildcard.
   *   This does not exclude any overloads that expect a SAM, because they conform to a function type through SAM conversion
   * - OR: all overloads expect a SAM type of the same class, but with potentially varying result types (argument types must be =:=)
   *       (this last case is not actually working due to a bug in scalac ¯\_(ツ)_/¯ https://github.com/scala/bug/issues/11703)
   * */
  private[this] def expectedFunctionTypeFromOverloadedAlternatives(
    alternatives: Seq[ScType],
    e:            PsiElement
  ): Option[ParameterType] = {
    import FunctionTypeMarker._

    def equiv(ltpe: ScType, rtpe: ScType): Boolean = {
      val comparingAbstractTypes = ltpe.is[ScAbstractType] && rtpe.is[ScAbstractType]
      ltpe.equiv(rtpe, ConstraintSystem.empty, falseUndef = !comparingAbstractTypes).isRight
    }

    implicit val scope: ElementScope = e.elementScope

    lazy val canMergeParamTpes = e.scalaLanguageLevelOrDefault >= Scala_2_13
    lazy val expectedArity     = aritiesOf(e)
    lazy val functionLikeType  = FunctionLikeType(e)

    def paramTpesMatch(lhs: Seq[ScType], rhs: Seq[ScType]): Boolean =
      lhs.isEmpty || lhs.corresponds(rhs)(equiv)

    @tailrec
    def recur(
      tpes:              Iterator[ScType],
      compatibleParams:  List[Seq[ScType]] = Nil,
      isFunctionN:       Boolean            = false,
      isPartialFunction: Boolean            = false,
      paramTpes:         Seq[ScType]        = Seq.empty,
      returnTpe:         Option[ScType]     = None
    ): (Option[ScType], List[Seq[ScType]]) =
      if (tpes.isEmpty) {
        val rtpe = returnTpe.getOrElse(Any)

        val mergedTpe =
          if (isPartialFunction) PartialFunctionType((rtpe, paramTpes.head)).toOption
          else if (isFunctionN)  FunctionType((rtpe, paramTpes)).toOption
          else                   None

        (mergedTpe, compatibleParams)
      } else tpes.next() match {
        case functionLikeType(marker, rtpe, ptpes) =>
          if (!expectedArity.matches(ptpes.size))
            /* Skip function like types with wrong arity */
            recur(tpes, compatibleParams, isFunctionN, isPartialFunction, paramTpes, returnTpe)
          else if (!paramTpesMatch(paramTpes, ptpes))
          /* One of the expected types is a function-like type of correct arity
             but with mismatched parameter types, FAIL */
            (None, Nil)
          else {
            val functionN        = isFunctionN       || marker == FunctionN
            val pf               = isPartialFunction || marker == PF
            val shouldSkip       = returnTpe.exists(equiv(_, rtpe))
            val uniqueCompatible = if (shouldSkip) compatibleParams else ptpes :: compatibleParams

            recur(
              tpes,
              uniqueCompatible,
              functionN,
              pf,
              ptpes,
              returnTpe.orElse(rtpe.toOption)
            )
          }
        case _ => (None, Nil)
      }

    def lubParamTpes(altParams: List[Seq[ScType]]): Option[ScType] =
      e match {
        case ref: ScReferenceExpression if !ScUnderScoreSectionUtil.isUnderscoreFunction(ref) => None
        case _ =>
          val paramLubs = altParams.transpose.map(_.lub())
          FunctionType((Any, paramLubs)).toOption
      }

    val (maybeMergedTpe, correctArity) = recur(alternatives.iterator)

    import org.jetbrains.plugins.scala.lang.psi.impl.expr.ExpectedTypesImpl.Arity.NotAFunction
    val result = expectedArity match {
      case NotAFunction => onlyOne(alternatives)
      case _ =>
        if (alternatives.size == 1) alternatives.headOption
        else if (canMergeParamTpes) maybeMergedTpe
        else                        lubParamTpes(correctArity)
    }

    result.map(_ -> None)
  }

  //Expression has no expected type if followed by "." + "Identifier expected" error, #SCL-15754
  private def isInIncompeteCode(e: ScExpression): Boolean = {
    def isIncompleteDot(e1: LeafPsiElement, e2: PsiErrorElement) =
      e1.textMatches(".") && e2.getErrorDescription == ScalaBundle.message("identifier.expected")

    e.nextSiblings.toSeq match {
      case Seq(e1: LeafPsiElement, e2: PsiErrorElement, _ @_*) if isIncompleteDot(e1, e2) => true
      case Seq(_: PsiWhiteSpace, e2: LeafPsiElement, e3: PsiErrorElement, _ @_*) if isIncompleteDot(e2, e3) => true
      case _ => false
    }
  }

  /**
   * @return (expectedType, expectedTypeElement)
   */
  override def expectedExprTypes(expr: ScExpression, withResolvedFunction: Boolean = false,
                                 fromUnderscore: Boolean = true): Array[ParameterType] = {
    import expr.projectContext

    if (expr.isPhysical && isInIncompeteCode(expr)) {
      return Array.empty
    }

    val sameInContext = expr.getDeepSameElementInContext

    def fromFunction(tp: ParameterType): Array[ParameterType] = {
      val functionLikeType = FunctionLikeType(expr)
      tp._1 match {
        case functionLikeType(_, retTpe, _) => Array((retTpe, None))
        case _                              => Array.empty
      }
    }

    def mapResolves(resolves: Array[ScalaResolveResult], types: Array[TypeResult]): Array[(TypeResult, Boolean)] =
      resolves.zip(types).map {
        case (r, tp) => (tp, isApplyDynamicNamed(r))
      }

    def argIndex(argExprs: Seq[ScExpression]) =
      if (sameInContext == null) 0
      else argExprs.indexWhere(_ == sameInContext).max(0)

    def expectedTypesForArg(invocation: MethodInvocation): Array[ParameterType] = {
      val argExprs = invocation.argumentExpressions
      val invoked  = invocation.getEffectiveInvokedExpr

      val tps = invoked match {
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
        case _ => Array((invoked.getNonValueType(), false))
      }

      val updatedWithExpected = tps.map {
        case (r, isDynamicNamed) => (r.map(invocation.updateAccordingToExpectedType), isDynamicNamed)
      }

      updatedWithExpected
        .filterNot(_._1.exists(_.equiv(Nothing)))
        .flatMap {
          case (r, isDynamicNamed) =>
            computeExpectedParamType(expr, r, argExprs, argIndex(argExprs), Some(invocation), isDynamicNamed = isDynamicNamed)
        }
    }

    val result: Array[ParameterType] = sameInContext.getContext match {
      case p: ScParenthesisedExpr => p.expectedTypesEx(fromUnderscore = false)
      //see SLS[6.11]
      case b: ScBlockExpr => b.resultExpression match {
        case Some(e) if b.needCheckExpectedType && e == sameInContext => b.expectedTypesEx(fromUnderscore = true)
        case _ => Array.empty
      }
      //see SLS[6.16]
      case cond: ScIf if cond.condition.getOrElse(null: ScExpression) == sameInContext => Array((api.Boolean, None))
      case cond: ScIf if cond.elseExpression.isDefined => cond.expectedTypesEx(fromUnderscore = true)
      //see SLA[6.22]
      case tr@ScTry(Some(e), _, _) if e == expr =>
        tr.expectedTypesEx(fromUnderscore = true)
      case wh: ScWhile if wh.condition.getOrElse(null: ScExpression) == sameInContext => Array((api.Boolean, None))
      case _: ScWhile => Array((Unit, None))
      case d: ScDo if d.condition.getOrElse(null: ScExpression) == sameInContext => Array((api.Boolean, None))
      case _: ScDo => Array((api.Unit, None))
      case _: ScFinallyBlock => Array((api.Unit, None))
      case _: ScCatchBlock => Array.empty
      case te: ScThrow =>
        // Not in the SLS, but in the implementation.
        val throwableClass = ScalaPsiManager.instance(te.getProject).getCachedClass(te.resolveScope, "java.lang.Throwable")
        val throwableType = throwableClass.map(new ScDesignatorType(_)).getOrElse(Any)
        Array((throwableType, None))
      //see SLS[8.4]
      case c: ScCaseClause => c.getContext.getContext match {
        case m: ScMatch => m.expectedTypesEx(fromUnderscore = true)
        case b: ScBlockExpr if b.isInCatchBlock =>
          b.getContext.getContext.asInstanceOf[ScTry].expectedTypesEx(fromUnderscore = true)
        case b: ScBlockExpr if b.isAnonymousFunction =>
          b.expectedTypesEx(fromUnderscore = true).flatMap(tp => fromFunction(tp))
        case _ => Array.empty
      }
      //see SLS[6.23]
      case f: ScFunctionExpr => f.expectedTypesEx(fromUnderscore = true).flatMap(tp => fromFunction(tp))
      case t: ScTypedExpression if t.getLastChild.isInstanceOf[ScSequenceArg] =>
        t.expectedTypesEx(fromUnderscore = true)
      //SLS[6.13]
      case t: ScTypedExpression =>
        t.typeElement match {
          case Some(te) => Array((te.`type`().getOrAny, Some(te)))
          case _ => Array.empty
        }
      //SLS[6.15]
      case a: ScAssignment if a.rightExpression.getOrElse(null: ScExpression) == sameInContext =>
        a.leftExpression match {
          case ref: ScReferenceExpression if (!a.getContext.isInstanceOf[ScArgumentExprList] && !(
            a.getContext.isInstanceOf[ScInfixArgumentExpression] && a.getContext.asInstanceOf[ScInfixArgumentExpression].isCall)) ||
                  ref.qualifier.isDefined ||
                  ScUnderScoreSectionUtil.isUnderscore(expr) /* See SCL-3512, SCL-3525, SCL-4809, SCL-6785 */ =>
            ref.bind() match {
              case Some(ScalaResolveResult(named: PsiNamedElement, subst: ScSubstitutor)) =>
                ScalaPsiUtil.nameContext(named) match {
                  case v: ScValue =>
                    Array((subst(named.asInstanceOf[ScTypedDefinition].
                      `type`().getOrAny), v.typeElement))
                  case v: ScVariable =>
                    Array((subst(named.asInstanceOf[ScTypedDefinition].
                      `type`().getOrAny), v.typeElement))
                  case f: ScFunction if f.paramClauses.clauses.isEmpty =>
                    a.mirrorMethodCall match {
                      case Some(call) =>
                        call.args.exprs.head.expectedTypesEx(fromUnderscore = fromUnderscore)
                      case None => Array.empty
                    }
                  case p: ScParameter =>
                    //for named parameters
                    Array((subst(p.`type`().getOrAny), p.typeElement))
                  case f: PsiField =>
                    Array((subst(f.getType.toScType()), None))
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
      case tuple: ScTuple if tuple.isCall => expectedTypesForArg(tuple.getContext.asInstanceOf[ScInfixExpr])
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
      case infix@ScInfixExpr.withAssoc(_, _, `sameInContext`) if !expr.isInstanceOf[ScTuple] =>
        val zExpr: ScExpression = expr match {
          case p: ScParenthesisedExpr => p.innerElement.getOrElse(return Array.empty)
          case _ => expr
        }
        expectedTypesForArg(infix)
      //SLS[4.1]
      case v @ ScPatternDefinition.expr(`sameInContext`)  if v.isSimple => declaredOrInheritedType(v)
      case v @ ScVariableDefinition.expr(`sameInContext`) if v.isSimple => declaredOrInheritedType(v)
      //SLS[4.6]
      case v: ScFunctionDefinition if v.body.contains(sameInContext) => declaredOrInheritedType(v)
      //default parameters
      case param: ScParameter =>
        param.typeElement match {
          case Some(_) => Array((param.`type`().getOrAny, param.typeElement))
          case _ => Array.empty
        }
      case ret: ScReturn =>
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
        args.getContext match {
          case mc: ScMethodCall => expectedTypesForArg(mc)
          case ctx @ (_: ScConstructorInvocation | _: ScSelfInvocation) =>
            val argExprs = args.exprs
            val argIdx = argIndex(argExprs)

            val tps = ctx match {
              case c: ScConstructorInvocation =>
                val clauseIdx = c.arguments.indexOf(args)

                if (!withResolvedFunction) c.shapeMultiType(clauseIdx)
                else c.multiType(clauseIdx)

              case s: ScSelfInvocation =>
                val clauseIdx = s.arguments.indexOf(args)

                if (!withResolvedFunction) s.shapeMultiType(clauseIdx)
                else s.multiType(clauseIdx)
            }

            tps.flatMap(computeExpectedParamType(expr, _, argExprs, argIdx))

          case _ =>
            Array.empty
        }
      case guard: ScGuard =>
        guard.desugared flatMap { _.content } match {
          case Some(content) => content.expectedTypesEx(fromUnderscore = fromUnderscore)
          case _ => Array.empty
        }
      case b: ScBlock if b.getContext.isInstanceOf[ScTry]
              || b.getContext.getContext.getContext.isInstanceOf[ScCatchBlock]
              || b.getContext.isInstanceOf[ScCaseClause]
              || b.getContext.isInstanceOf[ScFunctionExpr] => b.resultExpression match {
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

  @tailrec
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
        else
          params.map(
            p =>
              p.copy(
                paramType    = subst(p.paramType),
                expectedType = subst(p.expectedType)
              )
          )

      val autoTupling = newParams.length == 1 && !newParams.head.isRepeated && argExprs.length > 1

      if (autoTupling) {
        newParams.head.paramType.removeAbstracts match {
          case TupleType(args) =>
            paramTypeFromExpr(expr, paramsFromTuple(args), idx, isDynamicNamed)
          case _ => None
        }
      } else paramTypeFromExpr(expr, newParams, idx, isDynamicNamed)
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
      case Right(t @ ScTypePolymorphicType(ScMethodType(_, params, _), _)) =>
        val expectedType = call.flatMap(_.expectedType()).getOrElse(Any(expr))
        fromMethodTypeParams(params, t.argsProtoTypeSubst(expectedType))
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

  private def paramTypeFromExpr(expr: ScExpression, params: Seq[Parameter], idx: Int, isDynamicNamed: Boolean): Option[ParameterType] = {
    import expr.elementScope

    def findByIdx(params: Seq[Parameter]): ParameterType = {
      def simple = (params(idx).paramType, typeElem(params(idx)))
      def repeated = (params.last.paramType, typeElem(params.last))

      if (idx >= params.length)
        if (params.nonEmpty && params.last.isRepeated) repeated
        else                                           (Nothing, None)
      else simple
    }

    expr match {
      case assign: ScAssignment => Some {
        if (isDynamicNamed) paramTypeForDynamicNamed(findByIdx(params))
        else paramTypeForNamed(assign, params).getOrElse(findByIdx(params))
      }
      case typedStmt: ScTypedExpression if typedStmt.isSequenceArg && params.nonEmpty =>
        params.last.paramType.wrapIntoSeqType.map((_, None))
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

  private def paramTypeForNamed(assign: ScAssignment, params: Seq[Parameter]): Option[ParameterType] = {
    val lE = assign.leftExpression
    lE match {
      case ref: ScReferenceExpression if ref.qualifier.isEmpty =>
        params
          .find(parameter => ScalaNamesUtil.equivalent(parameter.name, ref.refName))
          .map (param => (param.paramType, typeElem(param)))
      case _ => None
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

    val superMemberAndSubstitutor = member match {
      case fun: ScFunction => fun.superMethodAndSubstitutor
      case other: ScMember => valSuperSignature(other).map(s => (s.namedElement, s.substitutor))
    }
    superMemberAndSubstitutor match {
      case Some((fun: ScFunction, subst)) =>
        val typeParamSubst =
          ScSubstitutor.bind(fun.typeParameters, typeParameters)(TypeParameterType(_))

        fun.returnType.toOption.map(typeParamSubst.followed(subst))
      case Some((fun: ScSyntheticFunction, _)) =>
        val typeParamSubst =
          ScSubstitutor.bind(fun.typeParameters, typeParameters)(TypeParameterType(_))

        Some(typeParamSubst(fun.retType))
      case Some((fun: PsiMethod, subst)) =>
        val typeParamSubst =
          ScSubstitutor.bind(fun.getTypeParameters, typeParameters)(TypeParameterType(_))

        Some(typeParamSubst.followed(subst)(fun.getReturnType.toScType()))
      case Some((t: Typeable, s: ScSubstitutor)) =>
        t.`type`().map(s).toOption
      case _ => None
    }
  }

  private def valSuperSignature(m: ScMember): Option[TermSignature] = {

    //expected type for values is not inherited from empty-parens functions
    def isParameterless(s: TermSignature) = s.namedElement match {
      case f: ScFunction                       => f.isParameterless
      case m: PsiMethod                        => !m.hasParameters
      case _: ScClassParameter                 => true
      case inNameContext(_: ScValueOrVariable) => true
      case _                                   => false
    }

    def superSignature(name: String, containingClass: PsiClass) = {
      val sigs = TypeDefinitionMembers.getSignatures(containingClass).forName(name)
      sigs.nodesIterator.collectFirst {
        case node if node.info.paramLength == 0 =>
          node.primarySuper.map(_.info).filter(isParameterless)
      }.flatten
    }

    val maybeName = m match {
      case v: ScValueOrVariable => v.declaredNames.headOption
      case cp: ScClassParameter if cp.isClassMember => Some(cp.name)
      case _ => None
    }

    for {
      name <- maybeName
      containingClass <- m.containingClass.toOption
      signature <- superSignature(name, containingClass)
    } yield {
      signature
    }
  }

}

private object ExpectedTypesImpl {
  private sealed trait Arity { def matches(arity: Int): Boolean }
  private object Arity {
    final case class TuplePattern(subpatterns: Int) extends Arity {
      override def matches(arity: Int): Boolean = subpatterns == arity || arity == 1
    }

    final case class FunctionLiteral(arity: Int) extends Arity {
      override def matches(arity: Int): Boolean = this.arity == arity
    }

    case object NotAFunction extends Arity { override def matches(arity: Int): Boolean = true }
  }


  implicit class ScMethodCallEx(private val invocation: MethodInvocation) extends AnyVal {

    def updateAccordingToExpectedType(`type`: ScType): ScType =
      InferUtil.updateAccordingToExpectedType(`type`, filterTypeParams = false, invocation.expectedType(), invocation, canThrowSCE = false)
  }

  implicit class ScExpressionForExpectedTypesEx(private val expr: ScExpression) extends AnyVal {
    @CachedWithRecursionGuard(expr, Array.empty[ScalaResolveResult], ModCount.getBlockModificationCount)
    def shapeResolveApplyMethod(tp: ScType, exprs: Seq[ScExpression], call: Option[MethodInvocation]): Array[ScalaResolveResult] = {
      val applyProc =
        new MethodResolveProcessor(expr, "apply", List(exprs), Seq.empty, Seq.empty /* todo: ? */ ,
          StdKinds.methodsOnly, isShapeResolve = true)
      applyProc.processType(tp, expr, ScalaResolveState.withFromType(tp))
      var cand = applyProc.candidates
      if (cand.length == 0 && call.isDefined) {
        val expr = call.get.getEffectiveInvokedExpr

        ImplicitResolveResult.processImplicitConversions("apply", expr, applyProc, precalculatedType = Some(tp)) {
          identity
        }(expr)
        cand = applyProc.candidates
      }
      if (cand.length == 0 && conformsToDynamic(tp, expr.resolveScope) && call.isDefined) {
        cand = ScalaPsiUtil.processTypeForUpdateOrApplyCandidates(call.get, tp, isShape = true, isDynamic = true)
      }
      cand
    }
  }

}
