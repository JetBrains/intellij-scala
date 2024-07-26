package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.MethodValue
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.extractImplicitParameterType
import org.jetbrains.plugins.scala.lang.psi.api.base.{ConstructorInvocationLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, UndefinedType, Unit}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectPsiElementExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.util.SAMUtil

import scala.meta.intellij.QuasiquoteInferUtil

object Compatibility {
  private lazy val LOG =
    Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.types.Compatibility")

  //TODO: get rid of this workaround
  // Why we even have this hack? Tests should work same way as produciton
  @TestOnly
  var seqClass: Option[PsiClass] = None

  trait Expression {
    /**
     * Returns actual type of an expression, after applying implicit conversions
     * and SAM adaptations, along with imports used in conversions.
     *
     * @param ignoreBaseTypes parameter to avoid value discarding, literal narrowing/widening,
     *                        useful for refactorings (introduce variable)
     * @param isShape         used during [[https://scala-lang.org/files/archive/spec/2.13/06-expressions.html#overloading-resolution overloading-resolution]]:<br>
     *                        The shape of an argument expression e, written shape(e), is a type that is defined as follows:
     *                        - For a function expression `(p1: T1,…,pn: Tn) => b`: `(Any ,…, Any) => shape(b)`,<br>
     *                          where Any occurs n times in the argument type.
     *                        - For a pattern-matching anonymous function definition `{ case ... }`: `PartialFunction[Any, Nothing]`
     *                        - For a named argument `n = e`: `shape(e)`
     *                        - For all other expressions: `Nothing`
     *
     */
    def getTypeAfterImplicitConversion(
      checkImplicits:  Boolean,
      isShape:         Boolean,
      expectedOption:  Option[ScType],
      ignoreBaseTypes: Boolean = false,
      fromUnderscore:  Boolean = false
    ): ExpressionTypeResult
  }

  object Expression {
    def apply(
      tpe:   ScType,
      place: Option[PsiElement] = None
    )(implicit
      ctx: ProjectContext
    ): Expression = OfType(tpe, place)

    def apply(tpe: ScType, place: PsiElement)(implicit ctx: ProjectContext): Expression =
      apply(tpe, Option(place))

    def unapply(e: Expression): Option[ScExpression] = e match {
      case e: ScExpression => Option(e)
      case _               => None
    }

    final case class OfType(tpe: ScType, place: Option[PsiElement]) extends Expression {
      private def default: ExpressionTypeResult = ExpressionTypeResult(Right(tpe))

      override def getTypeAfterImplicitConversion(
        checkImplicits:  Boolean,
        isShape:         Boolean,
        expectedOption:  Option[ScType],
        ignoreBaseTypes: Boolean,
        fromUnderscore:  Boolean
      ): ExpressionTypeResult =
        place.fold(default) { e =>
          if (isShape || !checkImplicits) default
          else cachedWithRecursionGuard("getTypeAfterImplicitConversion", e, ExpressionTypeResult(Right(tpe)), BlockModificationTracker(e), (e, tpe, expectedOption)) {
            expectedOption.collect {
              case etpe if !tpe.conforms(etpe) =>
                e.tryAdaptTypeToSAM(tpe, etpe, fromUnderscore = false, checkResolve = false, checkImplicits = checkImplicits)
                  .getOrElse(e.updateTypeWithImplicitConversion(tpe, etpe))
            }.getOrElse(default)
          }
        }
    }
  }

  implicit class ExpressionExt(private val expr: Expression) extends AnyVal {
    def scExpressionOrNull: ScExpression = expr match {
      case e: ScExpression => e
      case _               => null
    }
  }

  implicit class PsiElementExt(private val place: PsiElement) extends AnyVal {
    implicit def elementScope: ElementScope = ElementScope(place)

    final def tryAdaptTypeToSAM(
      tp:             ScType,
      pt:             ScType,
      fromUnderscore: Boolean,
      checkResolve:   Boolean = true,
      checkImplicits: Boolean = false
    ): Option[ExpressionTypeResult] = {
      def expectedResult(subst: ScSubstitutor): ScExpression.ExpressionTypeResult =
        ExpressionTypeResult(Right(subst(pt)))

      def conformanceSubst(tpe: ScType, methodType: ScType): Option[ScSubstitutor] = {
        val withUndefParams = methodType.updateLeaves {
          case abs: ScAbstractType => UndefinedType(abs.typeParameter)
        }

        val conformance = tpe.conforms(withUndefParams, ConstraintSystem.empty)

        if (conformance.isLeft) None
        else
          conformance.constraints
            .substitutionBounds(canThrowSCE = false)
            .map(_.substitutor)
      }

      def checkForSAM(etaExpansionHappened: Boolean = false): Option[ExpressionTypeResult] =
        tp match {
          case FunctionType(retTpe, params) if place.isSAMEnabled =>
            SAMUtil.toSAMType(pt, place) match {
              case Some(methodType @ FunctionType(ptRetTpe, _)) =>
                val maybeSubst = conformanceSubst(tp, methodType)

                maybeSubst match {
                  case Some(subst) => Option(expectedResult(subst))
                  case None if etaExpansionHappened =>
                    if (ptRetTpe.isUnit) {
                      val newTp = FunctionType(Unit, params)
                      conformanceSubst(newTp, methodType).map(expectedResult)
                    } else if (isNumericWidening(retTpe, ptRetTpe)) {
                      val newTp = FunctionType(ptRetTpe, params)
                      conformanceSubst(newTp, methodType).map(expectedResult)
                    } else if (checkImplicits) {
                      val implicitResult @ ExpressionTypeResult(Right(newRetTpe), _, _) =
                        updateTypeWithImplicitConversion(retTpe, ptRetTpe)

                      if (retTpe == newRetTpe) None
                      else {
                        val newTp = FunctionType(newRetTpe, params)
                        val maybeSubst = conformanceSubst(newTp, methodType)
                        maybeSubst.map { subst =>
                          implicitResult.copy(tr = Right(subst(pt)))
                        }
                      }
                    } else None
                  case _ => None
                }
              case _ => None
            }
          case _ => None
        }

      place match {
        case ScFunctionExpr(_, _) if fromUnderscore => checkForSAM()
        case ScUnderscoreSection.binding(ResolvesTo(param: ScParameter)) if param.isCallByNameParameter =>
          checkForSAM() // SCL-18195 `def bar(block: => Int): Foo = block _`
        case e: ScExpression if !fromUnderscore && ScalaPsiUtil.isAnonExpression(e) =>
          checkForSAM()
        case _ if !checkResolve => checkForSAM(etaExpansionHappened = true)
        case MethodValue(_)     => checkForSAM(etaExpansionHappened = true)
        case _                  => None
      }
    }

    final def updateTypeWithImplicitConversion(
      tpe:          ScType,
      expectedType: ScType
    ): ExpressionTypeResult = {
      val functionType = FunctionType(expectedType, Seq(tpe))

      val implicitCollector = new ImplicitCollector(
        place,
        functionType,
        functionType,
        None,
        isImplicitConversion = true
      )

      val fromImplicit = implicitCollector.collect() match {
        case Seq(res) =>
          extractImplicitParameterType(res).flatMap {
            case FunctionType(rt, Seq(_)) => Some(rt)
            case paramType =>
              elementScope.cachedFunction1Type.flatMap { functionType =>
                paramType.conforms(functionType, ConstraintSystem.empty) match {
                  case ConstraintSystem(substitutor) =>
                    Some(substitutor(functionType.typeArguments(1)))
                  case _ => None
                }
              }.filterNot(_.isInstanceOf[UndefinedType])
          }.map(_ -> res)
        case _ => None
      }

      fromImplicit match {
        case Some((mr, result)) =>
          ExpressionTypeResult(Right(mr), result.importsUsed, Some(result))
        case _ => ExpressionTypeResult(Right(tpe))
      }
    }
  }

  def seqTypeFor(expr: ScTypedExpression): Option[ScType] =
    seqClass.map(clazz =>
      if (ApplicationManager.getApplication.isUnitTestMode) ScDesignatorType(clazz)
      else throw new RuntimeException("Illegal state for seqClass variable")
    ).orElse(expr.elementScope.scalaSeqType)

  def checkConformance(
    parameters:         Seq[Parameter],
    exprs:              Seq[Expression],
    checkWithImplicits: Boolean
  ): ConstraintsResult = {
    val r = checkConformanceExt(
      parameters,
      exprs,
      checkWithImplicits,
      isShapesResolve = false
    )

    if (r.problems.nonEmpty) ConstraintsResult.Left
    else                     r.constraints
  }

  def clashedAssignmentsIn(exprs: Seq[Expression]): Seq[ScAssignment] = {
    val pairs = for(Expression(assignment @ ScAssignment.Named(name)) <- exprs) yield (name, assignment)
    val names = pairs.map(_._1)
    val clashedNames = names.diff(names.distinct)
    pairs.filter(p => clashedNames.contains(p._1)).map(_._2)
  }

  case class ConformanceExtResult(
    problems:             Seq[ApplicabilityProblem],
    constraints:          ConstraintSystem,
    defaultParameterUsed: Boolean = false,
    matched:              Seq[(Parameter, ScExpression, ScType)] = Seq.empty
  )

  object ConformanceExtResult {
    def apply(problems: Seq[ApplicabilityProblem])(implicit project: ProjectContext): ConformanceExtResult =
      ConformanceExtResult(problems, ConstraintSystem.empty)
  }

  def collectSimpleProblems(exprs: Seq[Expression], parameters: Seq[Parameter]): Seq[ApplicabilityProblem] = {
    val problems = Seq.newBuilder[ApplicabilityProblem]

    exprs.foldLeft(parameters) { (parameters, expression) =>
      expression match {
        case a: ScAssignment if a.referenceName.nonEmpty =>
          parameters.find(_.name == a.referenceName.get) match {
            case Some(parameter) =>
              parameters.filter(_ ne parameter)
            case None => parameters.tail
          }
        case _ => parameters.tail
      }
    }.foreach { param =>
      if (!param.isRepeated && !param.isDefault) {
        problems += MissedValueParameter(param)
      }
    }
    problems.result()
  }

  def checkConformanceExt(
    parameters:         Seq[Parameter],
    exprs:              Seq[Expression],
    checkWithImplicits: Boolean,
    isShapesResolve:    Boolean
  ): ConformanceExtResult = {
    ProgressManager.checkCanceled()
    var constraintAccumulator = ConstraintSystem.empty

    val clashedAssignments = clashedAssignmentsIn(exprs)

    if (clashedAssignments.nonEmpty) {
      val problems = clashedAssignments.map(ParameterSpecifiedMultipleTimes)
      return ConformanceExtResult(problems, constraintAccumulator)
    }

    //optimization:
    val hasRepeated = parameters.exists(_.isRepeated)
    val maxParams: Int = if (hasRepeated) scala.Int.MaxValue else parameters.length

    val excess = exprs.length - maxParams

    if (excess > 0) {
      val arguments = exprs.takeRight(excess).map(_.scExpressionOrNull)
      return ConformanceExtResult(arguments.map(ExcessArgument), constraintAccumulator)
    }

    val minParams = parameters.count(p => !p.isDefault && !p.isRepeated)
    if (exprs.length < minParams) {
      return ConformanceExtResult(collectSimpleProblems(exprs, parameters), constraintAccumulator)
    }

    if (parameters.isEmpty) {
      assert(exprs.isEmpty, "Empty exprs should have been handled by the excess check above")
      return ConformanceExtResult(Seq.empty, constraintAccumulator)
    }

    var parameterIndex = 0
    var namedMode = false //todo: optimization, when namedMode enabled, exprs.length <= parameters.length
    val used = new Array[Boolean](parameters.length)
    var problems: List[ApplicabilityProblem] = Nil
    val matched = Seq.newBuilder[(Parameter, ScExpression, ScType)]
    var defaultParameterUsed = false

    def doNoNamed(expr: Expression): List[ApplicabilityProblem] = {
      if (namedMode) {
        List(PositionalAfterNamedArgument(expr.scExpressionOrNull))
      }
      else {
        val getIt = used.indexOf(false)
        used(getIt) = true
        val param        = parameters(getIt)
        val paramType    = param.paramType
        val expectedType = param.expectedType

        val typeResult =
          expr.getTypeAfterImplicitConversion(
            checkWithImplicits, isShapesResolve, Option(expectedType)
          ).tr

        typeResult.toOption match {
          case None => Nil
          case Some(exprType) =>
            val conforms = exprType.weakConforms(paramType)
            matched.addOne(param, expr.scExpressionOrNull, exprType)
            if (!conforms) List(TypeMismatch(expr.scExpressionOrNull, paramType))
            else {
              constraintAccumulator += exprType.conforms(paramType, ConstraintSystem.empty, checkWeak = true).constraints
              List.empty
            }
        }
      }
    }

    while (parameterIndex < parameters.length.min(exprs.length)) {
      val expressionWithSameIndex = exprs(parameterIndex)
      expressionWithSameIndex match {
        case Expression(expr: ScTypedExpression) if expr.isSequenceArg =>
          seqTypeFor(expr) match {
            case Some(stpe) =>
              val getIt = used.indexOf(false)
              used(getIt) = true
              val param = parameters(getIt)

              if (!param.isRepeated) problems ::= ExpansionForNonRepeatedParameter(expr)

              val tpe = ScParameterizedType(stpe, Seq(param.paramType))
              val expectedTpe = ScParameterizedType(stpe, Seq(param.expectedType))

              for (exprType <- expr
                .getTypeAfterImplicitConversion(
                  checkWithImplicits,
                  isShapesResolve,
                  Some(expectedTpe)
                )
                .tr
                .toOption) {
                if (exprType.weakConforms(tpe)) {
                  matched.addOne((param, expr, exprType))
                  constraintAccumulator += exprType
                    .conforms(tpe, ConstraintSystem.empty, checkWeak = true)
                    .constraints
                } else {
                  return ConformanceExtResult(
                    Seq(TypeMismatch(expr, tpe)),
                    constraintAccumulator,
                    defaultParameterUsed,
                    matched.result()
                  )
                }
              }

            case None => problems :::= doNoNamed(expr).reverse
          }
        case Expression(assign @ ScAssignment.Named(name)) =>
          val index = parameters.indexWhere { p =>
            ScalaNamesUtil.equivalent(p.name, name) ||
              p.deprecatedName.exists(ScalaNamesUtil.equivalent(_, name))
          }
          if (index == -1 || used(index)) {
            def extractExpression(assign: ScAssignment): ScExpression = {
              if (ScUnderScoreSectionUtil.isUnderscoreFunction(assign)) assign
              else assign.rightExpression.getOrElse(assign)
            }
            val extracted = extractExpression(assign)

            if (extracted != assign) {
              //Named parameter case, note that assignment can also be a lambda, e.g. `foo = _`
              problems ::= WrongNamedParameterName(name)
            }

              problems :::= doNoNamed(extractExpression(assign)).reverse
          } else {
            used(index) = true
            val param: Parameter = parameters(index)
            if (index != parameterIndex) {
              namedMode = true
            }

            assign.rightExpression match {
              case rightExpression@Some(expr: ScExpression) =>
                val maybeSeqType = rightExpression.collect {
                  case typedExpr: ScTypedExpression if typedExpr.isSequenceArg => typedExpr
                }.flatMap {
                  seqTypeFor
                }

                maybeSeqType.foreach { _ =>
                  if (!param.isRepeated)
                    problems ::= ExpansionForNonRepeatedParameter(expr)
                }

                val (paramType, expectedType) = maybeSeqType.map { seqType =>
                  (ScParameterizedType(seqType, Seq(param.paramType)): ScType,
                    ScParameterizedType(seqType, Seq(param.expectedType)): ScType)
                }.getOrElse {
                  (param.paramType, param.expectedType)
                }

                for (exprType <- expr.getTypeAfterImplicitConversion(checkWithImplicits, isShapesResolve, Some(expectedType)).tr.toOption) {
                  if (exprType.weakConforms(paramType)) {
                    matched.addOne(param, expr, exprType)
                    constraintAccumulator += exprType.conforms(paramType, ConstraintSystem.empty, checkWeak = true).constraints
                  } else {
                    problems ::= TypeMismatch(expr, paramType)
                  }
                }
              case _ =>
                return ConformanceExtResult(Seq(IncompleteCallSyntax(ScalaBundle.message("assignment.missing.right.side"))), constraintAccumulator, defaultParameterUsed, matched.result())
            }
          }
        case expr: Expression =>
          problems :::= doNoNamed(expr).reverse
      }
      parameterIndex = parameterIndex + 1
    }

    if (problems.nonEmpty)
      return ConformanceExtResult(problems.reverse, constraintAccumulator, defaultParameterUsed, matched.result())

    if (exprs.length == parameters.length)
      return ConformanceExtResult(Seq.empty, constraintAccumulator, defaultParameterUsed, matched.result())
    else if (exprs.length > parameters.length) {
      if (namedMode) {
        // if we are in nameMode we cannot supply
        val excessiveExprs = exprs.drop(parameters.length).map(_.scExpressionOrNull)
        return ConformanceExtResult(excessiveExprs.map(ExcessArgument), constraintAccumulator, defaultParameterUsed, matched.result())
      }
      assert (parameters.last.isRepeated, "This case should have been handled by excessive check above")

      val paramType: ScType = parameters.last.paramType
      val expectedType: ScType = parameters.last.expectedType
      while (parameterIndex < exprs.length) {
        val expressionWithSameIndex = exprs(parameterIndex)
        for (exprType <- expressionWithSameIndex.getTypeAfterImplicitConversion(checkWithImplicits, isShapesResolve, Some(expectedType)).tr) {
          val conforms = exprType.weakConforms(paramType)
          if (!conforms) {
            val result = ConformanceExtResult(
              Seq(TypeMismatch(expressionWithSameIndex.scExpressionOrNull, paramType)),
              constraintAccumulator, defaultParameterUsed, matched.result()
            )
            return result
          } else {
            matched.addOne(parameters.last, expressionWithSameIndex.scExpressionOrNull, exprType)
            constraintAccumulator += exprType.conforms(paramType, ConstraintSystem.empty, checkWeak = true).constraints
          }
        }
        parameterIndex = parameterIndex + 1
      }
    }
    else {
      if (exprs.length == parameters.length - 1 && !namedMode && parameters.last.isRepeated)
        return ConformanceExtResult(Seq.empty, constraintAccumulator, defaultParameterUsed, matched.result())

      val missed = for ((parameter: Parameter, b) <- parameters.zip(used)
                        if !b && !parameter.isDefault) yield MissedValueParameter(parameter)
      defaultParameterUsed = parameters.zip(used).exists { case (param, bool) => !bool && param.isDefault}
      if (missed.nonEmpty)
        return ConformanceExtResult(missed, constraintAccumulator, defaultParameterUsed, matched.result())
      else {
        // inspect types default values
        val pack = parameters.zip(used)
        for ((param, use) <- pack if param.isDefault && !use) {
          val paramType = param.paramType
          param.defaultType match {
            case Some(defaultTp) if defaultTp.conforms(paramType) =>
              val expr = param.paramInCode
                .flatMap(_.getDefaultExpression)
                .get // safe (see defaultType implementation)
              matched.addOne(param, expr, defaultTp)

              constraintAccumulator += defaultTp.conforms(paramType, ConstraintSystem.empty).constraints
            case Some(defaultTp) =>
              return ConformanceExtResult(Seq(DefaultTypeParameterMismatch(defaultTp, paramType)), constraintAccumulator,
                defaultParameterUsed = true, matched.result())
            case _ =>
          }
        }
      }
    }
    ConformanceExtResult(Seq.empty, constraintAccumulator, defaultParameterUsed, matched.result())
  }

  def toParameter(p: ScParameter, substitutor: ScSubstitutor): Parameter = {
    val t = substitutor(p.`type`().getOrNothing)
    val default = p.getDefaultExpression.flatMap(_.`type`().toOption.map(substitutor))
    Parameter(p.name, p.deprecatedName, t, t, p.isDefaultParam, p.isRepeatedParameter, p.isCallByNameParameter,
      p.index, Some(p), default)
  }

  def compatible(
    srr:                ScalaResolveResult,
    substitutor:        ScSubstitutor,
    argClauses:         List[Seq[Expression]],
    checkWithImplicits: Boolean,
    isShapesResolve:    Boolean,
    ref:                PsiElement = null
  )(implicit
    project: ProjectContext
  ): ConformanceExtResult = {
    val named = srr.element

    def checkParameterListConformance(parameters: Seq[Parameter], arguments: Seq[Expression]): ConformanceExtResult =
      checkConformanceExt(parameters, arguments, checkWithImplicits, isShapesResolve)

    val firstArgumentListArgs: Seq[Expression] = argClauses.headOption.getOrElse(Seq.empty)

    named match {
      case synthetic: ScSyntheticFunction =>
        if (synthetic.paramClauses.isEmpty)
          return ConformanceExtResult(Seq(DoesNotTakeParameters))

        val parameters = synthetic.paramClauses.head.map(p =>
          p.copy(paramType = substitutor(p.paramType))
        )

        checkParameterListConformance(parameters, firstArgumentListArgs)
      case fun: ScFunction =>
        val isDefinedOrExportedInExtension = fun.isExtensionMethod || srr.exportedInExtension.isDefined

        if ((!fun.hasParameterClause && !isDefinedOrExportedInExtension) && argClauses.nonEmpty)
          return ConformanceExtResult(Seq(DoesNotTakeParameters))

        if (QuasiquoteInferUtil.isMetaQQ(fun) && ref.isInstanceOf[ScReferenceExpression]) {
          val params = QuasiquoteInferUtil.getMetaQQExpectedTypes(srr, ref.asInstanceOf[ScReferenceExpression])
          return checkParameterListConformance(params, firstArgumentListArgs)
        }

        val isQualifiedExtensionCall = srr.isExtensionCall

        def isRecursiveOrSameScopeExtensionCall =
          isDefinedOrExportedInExtension &&
            !isQualifiedExtensionCall &&
            (srr.extensionContext.nonEmpty && srr.extensionContext == fun.extensionMethodOwner)

        /**
         * We ignore parameter clauses coming from extension if:
         * 1. `fun` was invoked as a proper extension, e.g. `x.fun(bar)`
         * 2. `fun` was invoked from inside the body of an extension method defined in the
         * same collective extension as `fun`, e.g.
         * {{{
         * extension (x: T)
         *  def fun2 = fun
         *  def fun = ???
         * }}}
         */
        val shouldDropExtensionParameterClauses = isQualifiedExtensionCall || isRecursiveOrSameScopeExtensionCall

        val clauses =
          if (shouldDropExtensionParameterClauses) fun.effectiveParameterClauses
          else {
            val extensionOwner = srr.exportedInExtension
            fun.parameterClausesWithExtension(extensionOwner)
          }

        val firstClause = clauses.headOption

        val parameters =
          firstClause
            .toSeq
            .flatMap(_.effectiveParameters)
            .map(toParameter(_, substitutor))

        checkParameterListConformance(parameters, firstArgumentListArgs)
      case constructor: ScPrimaryConstructor =>
        val parameters = constructor.effectiveFirstParameterSection.map(toParameter(_, substitutor))
        checkParameterListConformance(parameters, firstArgumentListArgs)
      case method: PsiMethod =>
        val parameters = method.parameters.map(
          param =>
            Parameter(
              substitutor(param.paramType()),
              isRepeated = param.isVarArgs,
              index      = -1,
              param.getName
            )
        )

        checkParameterListConformance(parameters, firstArgumentListArgs)
      case unknown =>
        val problem = InternalApplicabilityProblem(ScalaBundle.message("cannot.handle.compatibility.for", unknown))
        LOG.error(problem.toString)
        ConformanceExtResult(Seq(problem))
    }
  }

  def checkConstructorConformance(
    constrInvocation: ConstructorInvocationLike,
    substitutor: ScSubstitutor,
    argClauses: Seq[ScArgumentExprList],
    paramClauses: Seq[ScParameterClause]
  )(implicit project: ProjectContext): ConformanceExtResult = {

    // a first empty argument clause might lack
    val nonEmptyArgClause =
      if (argClauses.isEmpty) Seq(Seq.empty)
      else argClauses.map(_.exprs)

    val (result, _) = nonEmptyArgClause.zip(paramClauses).foldLeft(ConformanceExtResult(Seq.empty) -> substitutor) {
      case ((prevRes, prevSubstitutor), (args, paramClause)) =>

        val params = paramClause.effectiveParameters.map(toParameter(_, prevSubstitutor))

        val eligibleForAutoTupling = args.length != 1 && params.length == 1 && !params.head.isDefault

        val curRes =
          checkConformanceExt(params, args, checkWithImplicits = true, isShapesResolve = false) match {
            case res if eligibleForAutoTupling && res.problems.nonEmpty =>
              // try autotupling. If the conformance check succeeds without problems we use that result
              ScalaPsiUtil
                .tupled(args, constrInvocation)
                .map(
                  checkConformanceExt(params, _, checkWithImplicits = true, isShapesResolve = true)
                )
                .filter(_.problems.isEmpty)
                .getOrElse(res)
            case res => res
          }

        val res = prevRes.copy(
          problems = prevRes.problems ++ curRes.problems,
          defaultParameterUsed = prevRes.defaultParameterUsed || curRes.defaultParameterUsed,
          matched = prevRes.matched ++ curRes.matched
        )

        val newSubstitutor = curRes.constraints match {
          case ConstraintSystem(csSubstitutor) => prevSubstitutor.followed(csSubstitutor)
          case _ => prevSubstitutor
        }

        res -> newSubstitutor
    }

    // in a constructor call all parameter clauses have to be supplied
    // Providing more clauses than required is ok, as those might be calls to apply
    // see: class A(i: Int) { def apply(j: Int) = ??? }
    // new A(2)(3) is ok
    val missedParameterClauseProblems = missedParameterClauseProblemsFor(paramClauses, nonEmptyArgClause.length, isConstructorInvocation = true)
    if (missedParameterClauseProblems.isEmpty) result
    else result.copy(problems = result.problems ++ missedParameterClauseProblems)
  }

  def missedParameterClauseProblemsFor(
    paramClauses: Seq[ScParameterClause],
    argClauseCount: Int,
    isConstructorInvocation: Boolean
  ): Seq[MissedParametersClause] = {
    var minParamClauses = paramClauses.length

    val hasImplicitClause = paramClauses.lastOption.exists(_.isImplicitOrUsing)
    //@TODO: multiple using clauses
    if (hasImplicitClause)
      minParamClauses -= 1

    val missedArgumentClauses = minParamClauses - argClauseCount
    if (missedArgumentClauses > 0) {
      val reportMissingClauses: Boolean = {
        val scalaLanguageLevel = paramClauses.headOption.map(_.scalaLanguageLevelOrDefault)
        val isBeforeScala213 = scalaLanguageLevel.exists(_ < ScalaLanguageLevel.Scala_2_13)
        if (isBeforeScala213 && isConstructorInvocation)
          true
        else
          paramClauses.drop(argClauseCount).exists(_.parameters.nonEmpty)
      }

      if (reportMissingClauses) {
        val missingClauses = paramClauses.drop(argClauseCount)
        missingClauses.map(MissedParametersClause.apply)
      }
      else Seq.empty
    }
    else Seq.empty
  }
}
