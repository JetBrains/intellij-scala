package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt => Ext, _}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.MethodValueExtractor
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.{ImplicitArgumentsClause, SafeCheckException, extractImplicitParameterType}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ConstructorInvocationLike, JavaConstructor, ScConstructorInvocation, ScMethodLike, ScPrimaryConstructor, ScalaConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, NamedTupleType, TupleType, TypeParameter, TypeParameterType, UndefinedType, Unit}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectPsiElementExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.scalaMeta.QuasiquoteInferUtil
import org.jetbrains.plugins.scala.util.SAMUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.{Ext => ScExpressionExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider.PsiMethodTypeProviderExt
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

import scala.annotation.tailrec

object Compatibility {
  private lazy val LOG =
    Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.types.Compatibility")

  //TODO: get rid of this workaround
  // Why we even have this hack? Tests should work same way as produciton
  @TestOnly
  var seqClass: Option[PsiClass] = None

  trait Expression {
    /**
     * Returns actual type of expression, after applying implicit conversions
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
      tpe: ScType,
      place: Option[PsiElement] = None
    )(implicit
      projectContext: ProjectContext,
      context: Context
    ): Expression = OfType(tpe, place)

    def apply(tpe: ScType, place: PsiElement)(implicit projectContext: ProjectContext, context: Context): Expression =
      apply(tpe, Option(place))

    def unapply(e: Expression): Option[ScExpression] = e match {
      case e: ScExpression => Option(e)
      case _ => None
    }

    final case class OfType(tpe: ScType, place: Option[PsiElement])(implicit projectContext: ProjectContext, context: Context) extends Expression {
      private def default: ExpressionTypeResult = ExpressionTypeResult(Right(tpe))

      override def getTypeAfterImplicitConversion(
        checkImplicits: Boolean,
        isShape: Boolean,
        expectedOption: Option[ScType],
        ignoreBaseTypes: Boolean,
        fromUnderscore: Boolean
      ): ExpressionTypeResult =
        place.fold(default) { e =>
          if (isShape) ExpressionTypeResult(Right(api.Nothing))
          else if (!checkImplicits) default
          else
            cachedWithRecursionGuard(
              "getTypeAfterImplicitConversion",
              e,
              default,
              BlockModificationTracker(e),
              (e, tpe, expectedOption)
            ) {
              expectedOption.collect {
                case etpe if !tpe.conforms(etpe) =>
                  e.tryAdaptTypeToSAM(
                    tpe,
                    etpe,
                    fromUnderscore = false,
                    checkResolve = false,
                    checkImplicits = checkImplicits
                  ).getOrElse(e.updateTypeWithImplicitConversion(tpe, etpe))
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
    private implicit def elementScope: ElementScope = ElementScope(place)
    private implicit def context: Context = Context(place)

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
                      val implicitResult@ExpressionTypeResult(Right(newRetTpe), _, _) =
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

      val methodValue = new MethodValueExtractor(Option(pt))

      place match {
        case ScFunctionExpr(_, _) if fromUnderscore => checkForSAM()
        case ScUnderscoreSection.binding(ResolvesTo(param: ScParameter)) if param.isCallByNameParameter =>
          checkForSAM() // SCL-18195 `def bar(block: => Int): Foo = block _`
        case e: ScExpression if !fromUnderscore && ScalaPsiUtil.isAnonExpression(e) =>
          checkForSAM()
        case _ if !checkResolve => checkForSAM(etaExpansionHappened = true)
        case methodValue(_)     => checkForSAM(etaExpansionHappened = true)
        case _                  => None
      }
    }

    final def updateTypeWithImplicitConversion(
      tpe: ScType,
      expectedType: ScType
    ): ExpressionTypeResult = {
      // A .toTuple call is inserted implicitly by the compiler
      // if it encounters a named tuple, but the expected type is a regular tuple.
      // (https://github.com/scala/scala3/blob/main/docs/_docs/reference/other-new-features/named-tuples.md#pattern-matching-with-named-fields-in-general)
      val afterNTtoTConversion =
        (tpe, expectedType) match {
          case (NamedTupleType(comps), TupleType(_)) =>
            TupleType(comps.map(_._2), scala3 = true)
          case _ =>
            tpe
        }

      val functionType = FunctionType(expectedType, Seq(afterNTtoTConversion))

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
              }.filterNot(_.is[UndefinedType])
          }.map(_ -> res)
        case _ => None
      }

      fromImplicit match {
        case Some((mr, result)) =>
          ExpressionTypeResult(Right(mr), result.importsUsed, Some(result))
        case _ => ExpressionTypeResult(Right(afterNTtoTConversion))
      }
    }
  }

  private def seqTypeFor(expr: ScTypedExpression): Option[ScType] =
    seqClass.map(clazz =>
      if (ApplicationManager.getApplication.isUnitTestMode) ScDesignatorType(clazz)
      else throw new RuntimeException("Illegal state for seqClass variable")
    ).orElse(expr.elementScope.scalaSeqType)

  def checkConformance(
    parameters:         Seq[Parameter],
    args:               Seq[Expression],
    checkWithImplicits: Boolean
  ): ConstraintsResult = {
    val r = checkMethodApplicability(
      parameters,
      args,
      checkWithImplicits,
      shapesOnly   = false
    )

    if (r.problems.nonEmpty) ConstraintsResult.Left
    else                     r.constraints
  }

  private def clashedAssignmentsIn(args: Seq[Expression]): Seq[ScAssignment] = {
    val assignments =
      for (Expression(assignment@ScAssignment.Named(name)) <- args)
        yield (name, assignment)

    val names = assignments.map(_._1)
    val clashedNames = names.diff(names.distinct)
    assignments.filter(p => clashedNames.contains(p._1)).map(_._2)
  }

  case class ApplicabilityCheckResult(
    problems:             Seq[ApplicabilityProblem],
    constraints:          ConstraintSystem,
    defaultParameterUsed: Boolean                                = false,
    matched:              Seq[(Parameter, ScExpression, ScType)] = Seq.empty
  )

  object ApplicabilityCheckResult {
    def apply(problems: Seq[ApplicabilityProblem]): ApplicabilityCheckResult =
      ApplicabilityCheckResult(problems, ConstraintSystem.empty)

    def apply(problem: ApplicabilityProblem): ApplicabilityCheckResult =
      ApplicabilityCheckResult(Seq(problem), ConstraintSystem.empty)
  }

  private def collectSimpleProblems(
    args:       Seq[Expression],
    parameters: Seq[Parameter]
  ): Seq[ApplicabilityProblem] = {
    val problems = Seq.newBuilder[ApplicabilityProblem]

    args
      .foldLeft(parameters) {
        (parameters, expression) =>
          expression match {
            case a: ScAssignment if a.referenceName.nonEmpty =>
              parameters.find(_.name == a.referenceName.get) match {
                case Some(parameter) => parameters.filter(_ ne parameter)
                case None            => parameters.tail
              }
            case _ => parameters.tail
          }
      }.foreach {
        param =>
          if (!param.isRepeated && !param.isDefault) {
            problems += MissedValueParameter(param)
          }
      }

    problems.result()
  }

  private def usingKw(arg: Expression): Option[PsiElement] =
    for {
      argPsi   <- arg.scExpressionOrNull.toOption
      usingKW  <- argPsi.prevSiblingNotWhitespaceComment
      if usingKW.getNode.getElementType == ScalaTokenType.UsingKeyword
    } yield usingKW

  def isExplicitUsingArgClause(args: Seq[Expression]): Boolean =
    args.headOption.flatMap(usingKw).isDefined

  /**
   * Calculates which parameter clause corresponds to an argument clause
   * at position `argClauseIdx` (useful in presence of multiple/interleaved using clauses)
   * e.g.:
   * {{{
   *   def foo[A](using Seq[A](x: A)(using B)(using C)(d: D): Int = ???
   *   foo("123")(using newB)(d)
   * }}}
   * Which parameter clause does argument expression `d` correspond to.
   */
  def correspondingParamClause(
    paramClauses: Seq[ScParameterClause],
    argClauses:   Seq[Seq[Expression]],
    argClauseIdx: Int
  ): Option[ScParameterClause] = {
    @tailrec
    def aux(idx: Int, paramClauseIndex: Int): Int =
      if (paramClauseIndex >= paramClauses.size) -1
      else if (idx >= argClauses.size)           -1
      else {
        val argsAtIdx       = argClauses(idx)
        val isExplicitUsing = isExplicitUsingArgClause(argsAtIdx)

        val isCurrentParamClauseUsing =
          paramClauseIndex < paramClauses.size &&
            paramClauses(paramClauseIndex).hasUsingKeyword

        if (!isExplicitUsing && isCurrentParamClauseUsing)
          aux(idx, paramClauseIndex + 1)
        else if (idx >= argClauseIdx)
          paramClauseIndex
        else aux(idx + 1, paramClauseIndex + 1)
      }

    val paramClauseIdx = aux(0, 0)
    paramClauses.lift(paramClauseIdx)
  }

  /**
   * @param withImplicits            When true, try implicit conversions in case `arg.type <!:< param.type`
   * @param shapesOnly               When true, only calculate shapeTypes of argument expressions
   * @param approximateDependentsFor See [[approximateDependent()]]
   */
  def checkMethodApplicability(
    parameters:               Seq[Parameter],
    args:                     Seq[Expression],
    withImplicits:            Boolean,
    shapesOnly:               Boolean,
    approximateDependentsFor: Set[ScParameter] = Set.empty
  )(implicit context: Context): ApplicabilityCheckResult = {

    ProgressManager.checkCanceled()

    var constraintAccumulator = ConstraintSystem.empty
    val clashedAssignments    = clashedAssignmentsIn(args)

    if (clashedAssignments.nonEmpty) {
      val problems = clashedAssignments.map(ParameterSpecifiedMultipleTimes)
      return ApplicabilityCheckResult(problems, constraintAccumulator)
    }

    //optimization:
    val hasRepeated = parameters.exists(_.isRepeated)
    val maxParams   = if (hasRepeated) scala.Int.MaxValue else parameters.length

    val excess = args.length - maxParams

    if (excess > 0) {
      val excessArguments = args.takeRight(excess).map(_.scExpressionOrNull)
      return ApplicabilityCheckResult(excessArguments.map(ExcessArgument), constraintAccumulator)
    }

    val minParams = parameters.count(p => !p.isDefault && !p.isRepeated)
    if (args.length < minParams) {
      return ApplicabilityCheckResult(collectSimpleProblems(args, parameters), constraintAccumulator)
    }

    if (parameters.isEmpty) {
      assert(args.isEmpty, "Empty args should have been handled by the excess check above")
      return ApplicabilityCheckResult(Seq.empty, constraintAccumulator)
    }

    var parameterIndex       = 0
    var namedMode            = false //todo: optimization, when namedMode enabled, args.length <= parameters.length
    val used                 = new Array[Boolean](parameters.length)
    var problems             = List.empty[ApplicabilityProblem]
    val matched              = Seq.newBuilder[(Parameter, ScExpression, ScType)]
    var defaultParameterUsed = false

    def processParamConformance(
      param: Parameter,
      pt:    ScType,
      arg:   Expression
    ): List[ApplicabilityProblem] = {
      val typeResult =
        arg.getTypeAfterImplicitConversion(
          withImplicits, shapesOnly, Option(param.expectedType)
        ).tr

      typeResult.toOption match {
        case None => Nil
        case Some(exprType) =>
          val approximatedPt = approximateDependent(pt, approximateDependentsFor).getOrElse(pt)
          val conforms = exprType.conforms(approximatedPt, ConstraintSystem.empty, checkWeak = true)
          matched.addOne(param, arg.scExpressionOrNull, exprType)

          conforms match {
            case ConstraintsResult.Left =>
              List(TypeMismatch(arg.scExpressionOrNull, pt))
            case cs: ConstraintSystem =>
              constraintAccumulator += cs
              List.empty
          }
      }
    }

    def processUnnamedArg(arg: Expression): List[ApplicabilityProblem] = {
      if (namedMode) {
        List(PositionalAfterNamedArgument(arg.scExpressionOrNull))
      } else {
        val idx = used.indexOf(false)

        used(idx) = true

        val param        = parameters(idx)
        val expectedType = param.paramType

        processParamConformance(param, expectedType, arg)
      }
    }

    val explicitUsingKw     = args.headOption.flatMap(usingKw)
    val isExplicitUsingArgs = explicitUsingKw.nonEmpty

    val isUsingParamClause = {
      val isInUsingClause = for {
        param    <- parameters.headOption
        psiParam <- param.paramInCode
      } yield psiParam.isInClauseWithUsing || psiParam.isInClauseWithImplicit

      isInUsingClause.getOrElse(false)
    }

    if (isExplicitUsingArgs && !isUsingParamClause)
      problems ::= UnexpectedUsingArgClause(explicitUsingKw.get)

    while (parameterIndex < parameters.length.min(args.length)) {
      val expressionWithSameIndex = args(parameterIndex)

      expressionWithSameIndex match {
        case Expression(expr: ScTypedExpression) if expr.isSequenceArg =>
          seqTypeFor(expr) match {
            case Some(stpe) =>
              val idx = used.indexOf(false)
              used(idx) = true
              val param = parameters(idx)

              if (!param.isRepeated) problems ::= ExpansionForNonRepeatedParameter(expr)

              val expectedType         = ScParameterizedType(stpe, Seq(param.paramType))
              val typeMismatchProblems = processParamConformance(param, expectedType, expr)

              if (typeMismatchProblems.nonEmpty)
                return ApplicabilityCheckResult(
                  typeMismatchProblems,
                  constraintAccumulator,
                  defaultParameterUsed,
                  matched.result()
                )

            case None => problems :::= processUnnamedArg(expr)
          }
        case Expression(assign@ScAssignment.Named(name)) =>
          val index = parameters.indexWhere { p =>
            ScalaNamesUtil.equivalent(p.name, name) ||
              p.deprecatedName.exists(ScalaNamesUtil.equivalent(_, name))
          }

          if (index == -1 || used(index)) {
            def extractExpression(assign: ScAssignment): ScExpression =
              if (ScUnderScoreSectionUtil.isUnderscoreFunction(assign))
                assign
              else
                assign.rightExpression.getOrElse(assign)

            val extracted = extractExpression(assign)

            if (extracted != assign) {
              //Named parameter case, note that assignment can also be a lambda, e.g. `foo = _`
              problems ::= WrongNamedParameterName(name)
            }

            problems :::= processUnnamedArg(extractExpression(assign))
          } else {
            used(index) = true
            val param = parameters(index)

            if (index != parameterIndex) {
              namedMode = true
            }

            assign.rightExpression match {
              case rightExpression@Some(expr: ScExpression) =>

                val maybeSeqType = rightExpression.collect {
                  case typedExpr: ScTypedExpression if typedExpr.isSequenceArg => typedExpr
                }.flatMap(seqTypeFor)

                maybeSeqType.foreach { _ =>
                  if (!param.isRepeated) problems ::= ExpansionForNonRepeatedParameter(expr)
                }

                val expectedType = maybeSeqType.map { seqType =>
                  ScParameterizedType(seqType, Seq(param.paramType))
                }.getOrElse(param.paramType)

                problems :::= processParamConformance(param, expectedType, expr)
              case _ =>
                return ApplicabilityCheckResult(
                  Seq(IncompleteCallSyntax(ScalaBundle.message("assignment.missing.right.side"))),
                  constraintAccumulator,
                  defaultParameterUsed,
                  matched.result()
                )
            }
          }
        case expr: Expression =>
          problems :::= processUnnamedArg(expr)
      }
      parameterIndex = parameterIndex + 1
    }

    if (problems.nonEmpty)
      return ApplicabilityCheckResult(problems.reverse, constraintAccumulator, defaultParameterUsed, matched.result())

    if (args.length == parameters.length)
      return ApplicabilityCheckResult(Seq.empty, constraintAccumulator, defaultParameterUsed, matched.result())
    else if (args.length > parameters.length) {
      if (namedMode) {
        // We cannot have repeated parameter, if we are in namedMode
        val excessiveArgs = args.drop(parameters.length).map(_.scExpressionOrNull)

        return ApplicabilityCheckResult(
          excessiveArgs.map(ExcessArgument),
          constraintAccumulator,
          defaultParameterUsed,
          matched.result()
        )
      }
      assert(parameters.last.isRepeated, "This case should have been handled by excessive check above")

      val param = parameters.last
      val expectedType = param.paramType

      while (parameterIndex < args.length) {
        val expressionWithSameIndex = args(parameterIndex)

        val typeMismatchProblem = processParamConformance(param, expectedType, expressionWithSameIndex)
        if (typeMismatchProblem.nonEmpty) {
          return ApplicabilityCheckResult(
            typeMismatchProblem,
            constraintAccumulator,
            defaultParameterUsed,
            matched.result()
          )
        }
        parameterIndex = parameterIndex + 1
      }
    } else {
      //Empty repeated parameter case:
      if (args.length == parameters.length - 1 && !namedMode && parameters.last.isRepeated)
        return ApplicabilityCheckResult(Seq.empty, constraintAccumulator, defaultParameterUsed, matched.result())

      val missed =
        for (
          (parameter: Parameter, b) <- parameters.zip(used)
          if !b && !parameter.isDefault
        ) yield MissedValueParameter(parameter)

      defaultParameterUsed = parameters.zip(used).exists { case (param, bool) => !bool && param.isDefault }

      if (missed.nonEmpty)
        return ApplicabilityCheckResult(
          missed,
          constraintAccumulator,
          defaultParameterUsed,
          matched.result()
        )
      else {
        // Inspect types of default parameter values
        val parametersUsage = parameters.zip(used)

        for ((param, isUsed) <- parametersUsage if param.isDefault && !isUsed) {
          val paramType = param.paramType

          param.defaultType match {
            case Some(defaultTp) if defaultTp.conforms(paramType) =>
              val expr =
                param.paramInCode
                  .flatMap(_.getDefaultExpression)
                  .get // safe (see defaultType implementation)

              matched.addOne(param, expr, defaultTp)

              constraintAccumulator += defaultTp
                .conforms(paramType, ConstraintSystem.empty)
                .constraints
            case Some(defaultTp) =>
              return ApplicabilityCheckResult(
                Seq(DefaultTypeParameterMismatch(defaultTp, paramType)),
                constraintAccumulator,
                defaultParameterUsed = true,
                matched.result()
              )
            case _ =>
          }
        }
      }
    }

    ApplicabilityCheckResult(Seq.empty, constraintAccumulator, defaultParameterUsed, matched.result())
  }

  def toParameter(p: ScParameter, substitutor: ScSubstitutor): Parameter = {
    val tpe     = substitutor(p.`type`().getOrNothing)
    val default = p.getDefaultExpression.flatMap(_.`type`().toOption.map(substitutor))

    Parameter(
      p.name,
      p.deprecatedName,
      tpe,
      tpe,
      p.isDefaultParam,
      p.isRepeatedParameter,
      p.isCallByNameParameter,
      p.index,
      Option(p),
      default
    )
  }

  def compatible(
    srr:           ScalaResolveResult,
    substitutor:   ScSubstitutor,
    argClauses:    Seq[Seq[Expression]],
    withImplicits: Boolean,
    shapesOnly:    Boolean,
    ref:           PsiElement,
    argClauseIdx:  Int = 0
  )(implicit context: Context): ApplicabilityCheckResult = {
    val named = srr.element
    val args  = argClauses.lift(argClauseIdx).getOrElse(Seq.empty)

    def checkParameterListConformance(
      parametersForCurrentClause: Seq[Parameter],
      allImplicitParameters:      Set[ScParameter] = Set.empty,
    ): ApplicabilityCheckResult =
      checkMethodApplicability(
        parametersForCurrentClause,
        args,
        withImplicits,
        shapesOnly,
        allImplicitParameters
      )

    named match {
      case synthetic: ScSyntheticFunction =>
        val paramClauses = synthetic.paramClauses

        if (paramClauses.isEmpty || argClauseIdx > 0)
          return ApplicabilityCheckResult(DoesNotTakeParameters)

        val parameters = paramClauses(argClauseIdx).map(p =>
          p.copy(paramType = substitutor(p.paramType))
        )

        checkParameterListConformance(parameters)
      case fun: ScFunction =>
        val isDefinedOrExportedInExtension = fun.isExtensionMethod || srr.exportedInExtension.isDefined

        if ((!fun.hasParameterClause && !isDefinedOrExportedInExtension) && args.nonEmpty)
          return ApplicabilityCheckResult(DoesNotTakeParameters)

        if (QuasiquoteInferUtil.isMetaQQ(fun) && ref.is[ScReferenceExpression]) {
          val params = QuasiquoteInferUtil.getMetaQQExpectedTypes(srr, ref.asInstanceOf[ScReferenceExpression])
          return checkParameterListConformance(params)
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

        val currentClause = correspondingParamClause(clauses, argClauses, argClauseIdx)

        currentClause match {
          case Some(clause) =>
            val parameters = clause.effectiveParameters.map(toParameter(_, substitutor))
            val allImplicitParameters =
              clauses
                .view
                .flatMap(_.effectiveParameters)
                .filter(_.isImplicit)
                .to(Set)

            checkParameterListConformance(parameters, allImplicitParameters)
          case None => ApplicabilityCheckResult(DoesNotTakeParameters)
        }
      case constructor: ScPrimaryConstructor =>
        val parameters = constructor.effectiveFirstParameterSection.map(toParameter(_, substitutor))
        checkParameterListConformance(parameters)
      case method: PsiMethod =>
        if (argClauseIdx > 0)
          return ApplicabilityCheckResult(DoesNotTakeParameters)

        val parameters = method.parameters.map(
          param =>
            Parameter(
              substitutor(param.paramType()),
              isRepeated = param.isVarArgs,
              index = -1,
              param.getName
            )
        )

        checkParameterListConformance(parameters)
      case unknown =>
        val problem = InternalApplicabilityProblem(ScalaBundle.message("cannot.handle.compatibility.for", unknown))
        LOG.error(problem.toString)
        ApplicabilityCheckResult(Seq(problem))
    }
  }

  def checkConstructorApplicability(
    constrInvocation: ConstructorInvocationLike,
    cons:             PsiMethod,
    srr:              ScalaResolveResult,
    inferValueType:   Boolean = false
  )(implicit
    ctx: ProjectContext
  ): (ScType, ApplicabilityCheckResult, Seq[ImplicitArgumentsClause]) = {
    val args                       = constrInvocation.arguments
    val argExprs                   = args.map(_.exprs)
    val shouldInjectEmptyArgClause = args.forall(_.isUsing)

    val nonEmptyArgs =
      if (shouldInjectEmptyArgClause) Seq(Seq.empty[Expression]) ++ argExprs
      else                            argExprs

    def retryWithAutoTupling(
      f:      Seq[Expression] => (ScType, ApplicabilityCheckResult),
      args:   Seq[Expression],
      params: Seq[Parameter]
    ): (ScType, ApplicabilityCheckResult) = {
      val eligibleForAutoTupling: Boolean =
        args.length != 1 && params.length == 1 && !params.head.isDefault

      f(args) match {
        case res @ (_, checkRes) if eligibleForAutoTupling && checkRes.problems.nonEmpty =>
          ScalaPsiUtil
            .tupled(args, constrInvocation)
            .map(f)
            .filter(_._2.problems.isEmpty)
            .getOrElse(res)
        case other => other
      }
    }

    def updateWithClause(
      prevResult:  ApplicabilityCheckResult,
      tpe:         ScType,
      args:        Seq[Expression],
      canThrowSCE: Boolean
    ): (ScType, ApplicabilityCheckResult) =
      tpe match {
        case ScTypePolymorphicType(ScMethodType(retType, params, _), typeParams) =>
          retryWithAutoTupling(
            InferUtil.localTypeInferenceWithApplicabilityExt(
              retType,
              params,
              _,
              typeParams,
              canThrowSCE = canThrowSCE,
            ),
            args,
            params
          )
        case ScMethodType(resTpe, params, _) =>
          retryWithAutoTupling(
            args => {
              val checkRes = checkMethodApplicability(
                params,
                args,
                withImplicits = true,
                shapesOnly    = false,
              )
              (resTpe, checkRes)
            },
            args,
            params
          )
        case other => (other, prevResult)
      }

    def updateScalaConstructorType(
      scalaCons:            ScMethodLike,
      previousRes:          ApplicabilityCheckResult,
      tpe:                  ScType,
      stopBeforeLastClause: Boolean,
      clauseIdx:            Int,
      withExpected:         Boolean = false
    ): (ScType, ApplicabilityCheckResult, Seq[ImplicitArgumentsClause]) = {
      var i                   = clauseIdx
      var resTpe              = tpe
      var applicabilityRes    = previousRes
      val implicitArgsBuilder = Seq.newBuilder[ImplicitArgumentsClause]

      val paramClauses = scalaCons.effectiveParameterClauses
      val clauseBound  = nonEmptyArgs.length - (if (stopBeforeLastClause) 1 else 0)

      while (i < clauseBound) {
        val (updatedRes, clauseApplicability) = updateWithClause(
          previousRes,
          resTpe,
          nonEmptyArgs(i),
          canThrowSCE = withExpected
        )

        resTpe = updatedRes

        applicabilityRes = applicabilityRes.copy(
          problems             = applicabilityRes.problems ++ clauseApplicability.problems,
          defaultParameterUsed = applicabilityRes.defaultParameterUsed || clauseApplicability.defaultParameterUsed,
          matched              = applicabilityRes.matched ++ clauseApplicability.matched
        )

        val shouldNotUpdateTrailingImplicits = {
          val nextArgClauseIdx = i + (if (shouldInjectEmptyArgClause) 0 else 1)

          args.nonEmpty &&
            (args.lift(nextArgClauseIdx) match {
            case Some(argList) =>
              argList.isUsing || {
                val nextParamClause =
                  Compatibility.correspondingParamClause(
                    paramClauses,
                    nonEmptyArgs,
                    i + 1
                  )

                nextParamClause.exists(_.hasImplicitKeyword)
              }
            case _ => false
          })
        }

        if (!shouldNotUpdateTrailingImplicits) {
          val (updatedWithImplicits, implicitArgs) = updateTypeWithImplicitArguments(
            resTpe,
            withExpected      = withExpected,
            hasImplicitClause = true,
            isLeadingClause   = false
          )

          implicitArgsBuilder ++= implicitArgs
          resTpe                = updatedWithImplicits
        }

        i += 1
      }

      (resTpe, applicabilityRes, implicitArgsBuilder.result())
    }

    def updateWithExpected(tpe: ScType, expected: ScType): ScType = tpe match {
      case tpt: ScTypePolymorphicType =>
        InferUtil.updateAccordingToExpectedType(
          tpt,
          filterTypeParams = false,
          Option(expected),
          constrInvocation,
          canThrowSCE = true
        )
      case _ => tpe
    }

    def updateTypeWithImplicitArguments(
      tp:                ScType,
      withExpected:      Boolean,
      hasImplicitClause: Boolean,
      isLeadingClause:   Boolean,
    ): (ScType, Seq[ImplicitArgumentsClause]) = {
      val (innerRes, implicitArgs) =
        if (hasImplicitClause) {
          val (updatedTp, implicitsByClause) =
            InferUtil.updateTypeWithImplicitParameters(
              tp,
              constrInvocation,
              None,
              withExpected,
              fullInfo        = false,
              updateDeep      = !isLeadingClause,
              isLeadingClause = isLeadingClause
            )

          (updatedTp, implicitsByClause)
        } else (tp, Seq.empty)

      (innerRes, implicitArgs)
    }

    def lastClauseAndImplicits(
      previousTpe:  ScType,
      previousRes:  ApplicabilityCheckResult,
      withExpected: Boolean
    ): (ScType, ApplicabilityCheckResult, Seq[ImplicitArgumentsClause]) = {
      val expectedType = constrInvocation match {
        case consInv: ScConstructorInvocation => consInv.expectedType
        case _: ScSelfInvocation              => None
        case other                            =>
          throw new IllegalArgumentException(ScalaBundle.message("unknown.constructor.invocation", other.getClass))
      }

      val typeWithExpected = expectedType match {
        case Some(expected) if withExpected =>
          val fromUnderscore = {
            val underscores = for {
              consInv  <- constrInvocation.asOptionOf[ScConstructorInvocation]
              template <- consInv.newTemplate
            } yield ScUnderScoreSectionUtil.underscores(template).nonEmpty

            underscores.getOrElse(false)
          }

          if (!fromUnderscore) updateWithExpected(previousTpe, expected)
          else expected match {
            case FunctionType(retType, _) => updateWithExpected(previousTpe, retType)
            case _                        => previousTpe //do not update res, we haven't expected type
          }
        case _ => previousTpe
      }

      cons match {
        case scalaCons: ScMethodLike =>
          val idx = Math.max(0, nonEmptyArgs.size - 1)

          updateScalaConstructorType(
            scalaCons,
            previousRes,
            typeWithExpected,
            stopBeforeLastClause = false,
            clauseIdx            = idx,
            withExpected         = withExpected
          )
        case _ =>
          val args = nonEmptyArgs.head

          val (resTpe, applicabilityRes) = updateWithClause(
            previousRes,
            typeWithExpected,
            args,
            canThrowSCE = withExpected
          )

          (resTpe, applicabilityRes, Seq.empty)
      }
    }

    val hasImplicitClause = cons match {
      case fn: ScMethodLike => fn.effectiveParameterClauses.exists(_.isImplicit)
      case _                => false
    }

    val constructorTypeParameters = cons match {
      case ScalaConstructor(cons) => cons.getConstructorTypeParameters
      case JavaConstructor(cons)  => cons.getTypeParameters.toSeq
      case _                      => Seq.empty
    }

    val (typeParameters, bindSubst) = srr.getActualElement match {
      case to: ScTypeParametersOwner if constructorTypeParameters.nonEmpty =>
        val subst  = ScSubstitutor.bind(to.typeParameters, constructorTypeParameters)(TypeParameterType(_))
        val params = constructorTypeParameters.map(TypeParameter(_))
        (params, subst)
      case tp: ScTypeParametersOwner if tp.typeParameters.nonEmpty =>
        val params = tp.typeParameters.map(TypeParameter(_))
        (params, ScSubstitutor.empty)
      case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.nonEmpty =>
        val params = (ptp.getTypeParameters.toSeq ++ constructorTypeParameters).map(TypeParameter(_))
        (params, ScSubstitutor.empty)
      case _ => (Seq.empty, ScSubstitutor.empty)
    }

    val typeArgs      = constrInvocation.typeArgList.map(_.typeArgs).getOrElse(Seq.empty)
    val typeArgsSubst = ScSubstitutor.bind(typeParameters, typeArgs)(_.calcType)
    val subst         = srr.substitutor.followed(bindSubst).followed(typeArgsSubst)
    val methodType    = subst(cons.methodTypeProvider(constrInvocation.elementScope).methodType())

    val consType =
      srr.getActualElement match {
        case ta: ScTypeAliasDefinition if ScalaApplicationSettings.PRECISE_TEXT =>
          val ref     = constrInvocation.asOptionOf[ScConstructorInvocation].flatMap(_.reference)

          val refType =
            ref
              .flatMap(ScSimpleTypeElementImpl.calculateReferenceType(_).toOption)
              .getOrElse(api.Nothing)

          ScSimpleTypeElementImpl.parameterizeTypeAlias(
            refType,
            ta
          )
        case _ =>
          if (typeParameters.nonEmpty) ScTypePolymorphicType(methodType, typeParameters)
          else                         methodType
      }

    val initialApplicabilityRes = ApplicabilityCheckResult(Seq.empty)

    val (typeWithoutLeadingImplicits, leadingImplicitArgs) = updateTypeWithImplicitArguments(
      consType,
      withExpected      = false,
      hasImplicitClause = hasImplicitClause,
      isLeadingClause   = true
    )

    val (tpeWithoutLast, applicabilityResWithoutLastClause, implicitArgs) = cons match {
      case scalaCons: ScMethodLike =>
        updateScalaConstructorType(
          scalaCons,
          initialApplicabilityRes,
          typeWithoutLeadingImplicits,
          stopBeforeLastClause = true,
          clauseIdx            = 0
        )
      case _ =>
        //java constructor can only have 1 parameter clause, so no need to do anything here
        (typeWithoutLeadingImplicits, ApplicabilityCheckResult(Seq.empty), Seq.empty)
    }

    val (resTpe, applicabilityRes, trailingImplicitArgs)  =
      try lastClauseAndImplicits(tpeWithoutLast, applicabilityResWithoutLastClause, withExpected = true)
      catch {
        case _: SafeCheckException =>
          lastClauseAndImplicits(tpeWithoutLast, applicabilityResWithoutLastClause, withExpected = false)
      }

    val tpe =
      if (inferValueType) resTpe.inferValueType
      else                resTpe

    val withMissedParameterClauseProblems = cons match {
      case scalaCons: ScMethodLike =>
        val missedParameterClauseProblems =
          missedParameterClauseProblemsFor(
            scalaCons.effectiveParameterClauses,
            nonEmptyArgs.length,
            isConstructorInvocation = true
          )

        if (missedParameterClauseProblems.isEmpty) applicabilityRes
        else
          applicabilityRes.copy(problems = applicabilityRes.problems ++ missedParameterClauseProblems)
      case _ => applicabilityRes
    }

    (tpe, withMissedParameterClauseProblems, leadingImplicitArgs ++ implicitArgs ++ trailingImplicitArgs)
  }

  def missedParameterClauseProblemsFor(
    paramClauses:            Seq[ScParameterClause],
    argClauseCount:          Int,
    isConstructorInvocation: Boolean
  ): Seq[MissedParametersClause] = {
    val implicitClauses = paramClauses.filter(_.isImplicit)
    val minParamClauses = paramClauses.length - implicitClauses.length

    val missedArgumentClauses = minParamClauses - argClauseCount
    if (missedArgumentClauses > 0) {
      val reportMissingClauses: Boolean = {
        val scalaLanguageLevel = paramClauses.headOption.map(_.scalaLanguageLevelOrDefault)
        val isBeforeScala213   = scalaLanguageLevel.exists(_ < ScalaLanguageLevel.Scala_2_13)

        if (isBeforeScala213 && isConstructorInvocation)
          true
        else
          paramClauses.drop(argClauseCount).exists(_.parameters.nonEmpty)
      }

      if (reportMissingClauses) {
        val missingClauses = paramClauses.drop(argClauseCount)
        missingClauses.map(MissedParametersClause.apply)
      } else Seq.empty
    } else Seq.empty
  }

  /** Dependency on an implicit argument is like a dependency on type parameter, thus
   * before checking implicit return type conformance we have to substitute parameter-dependent
   * types with `UndefinedType`, otherwise compatibility check is bound to fail.
   * We also have to verify (after we successfully found some implicit to be compatible)
   * that result type with argument-dependent types restored does indeed conform to `tp`.
   *
   * Upd. 19.05.2025 Same logic applies to overloading resolution with leading using clauses.
   * e.g.
   * {{{
   *   trait Foo[A] { type X }
   *   def f[A](using f: Foo[A])(using f.X)(a: f.X): f.X = ???
   *   def f(a: String): String = ???
   *   val s = f(1)
   * }}}
   * This is NOT an ambiguous overload as one might suspect,
   * first alternative is deemed applicable by the compiler.
   *
   * @param tpe Return type of implicit currently undergoing a compatibility check
   * @return `tpe` with parameter-dependent types replaced with `UndefinedType`s,
   *         and a mean of reverting this process (useful once type parameters have been inferred
   *         and dependents need to actually be updated according to argument types)
   */
  def approximateDependent(tpe: ScType, params: Set[ScParameter]): Option[ScType] = {
    var hasDependents = false

    val updated = tpe.updateRecursively {
      case original @ ScProjectionType(ScDesignatorType(p: ScParameter), _) if params.contains(p) =>
        hasDependents = true
        UndefinedType(p, original)
    }

    Option.when(hasDependents)(updated)
  }
}