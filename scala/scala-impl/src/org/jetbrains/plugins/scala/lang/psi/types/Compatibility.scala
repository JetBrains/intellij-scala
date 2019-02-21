package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.impl.compiled.ClsParameterImpl
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.extractImplicitParameterType
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.mutable.ArrayBuffer
import scala.collection.{Seq, Set}
import scala.meta.intellij.QuasiquoteInferUtil

/**
 * @author ven
 */
object Compatibility {
  @TestOnly
  var seqClass: Option[PsiClass] = None

  case class Expression(expr: ScExpression)(implicit pc: ProjectContext) {

    var typez: ScType = null
    var place: PsiElement = null
    def this(tp: ScType)(implicit pc: ProjectContext) = {
      this(null: ScExpression)
      typez = tp
    }
    def this(tp: ScType, place: PsiElement)(implicit pc: ProjectContext) {
      this(tp)
      this.place = place
    }


    @CachedWithRecursionGuard(place, (Right(typez), Set.empty), ModCount.getBlockModificationCount)
    private def eval(typez: ScType, expectedOption: Option[ScType]): (TypeResult, Set[ImportUsed]) =
      expectedOption.filterNot(typez.conforms(_)).flatMap { expected =>
        implicit val elementScope: ElementScope = place.elementScope

        val functionType = FunctionType(expected, Seq(typez))
        new ImplicitCollector(place, functionType, functionType, None, isImplicitConversion = true).collect() match {
          case Seq(res) =>
            extractImplicitParameterType(res).flatMap {
              case FunctionType(rt, Seq(_)) => Some(rt)
              case paramType =>
                elementScope.cachedFunction1Type.flatMap { functionType =>
                  paramType.conforms(functionType, ConstraintSystem.empty) match {
                    case ConstraintSystem(substitutor) => Some(substitutor(functionType.typeArguments(1)))
                    case _ => None
                  }
                }.filterNot {
                  _.isInstanceOf[UndefinedType]
                }
            }.map { result =>
              (Right(result), res.importsUsed)
            }
          case _ => None
        }
      }.getOrElse {
        (Right(typez), Set.empty)
      }

    def getTypeAfterImplicitConversion(checkImplicits: Boolean, isShape: Boolean,
                                       expectedOption: Option[ScType]): (TypeResult, collection.Set[ImportUsed]) = {
      if (expr != null) {
        val expressionTypeResult = expr.getTypeAfterImplicitConversion(checkImplicits, isShape, expectedOption)
        (expressionTypeResult.tr, expressionTypeResult.importsUsed)
      } else {
        import scala.collection.Set

        def default: (TypeResult, Set[ImportUsed]) = (Right(typez), Set.empty)

        if (isShape || !checkImplicits || place == null) default
        else eval(typez, expectedOption)
      }
    }
  }

  object Expression {
    import scala.language.implicitConversions

    implicit def scExpression2Expression(expr: ScExpression)(implicit pc: ProjectContext): Expression = Expression(expr)
    implicit def seq2ExpressionSeq(seq: Seq[ScExpression])(implicit pc: ProjectContext): Seq[Expression] = seq.map(Expression(_))
    implicit def args2ExpressionArgs(list: List[Seq[ScExpression]])(implicit pc: ProjectContext): List[Seq[Expression]] = {
      list.map(_.map(Expression(_)))
    }
  }

  def seqTypeFor(expr: ScTypedExpression): Option[ScType] =
    seqClass.map { clazz =>
      if (ApplicationManager.getApplication.isUnitTestMode) clazz
      else throw new RuntimeException("Illegal state for seqClass variable")
    }.orElse {
      expr.elementScope.getCachedClass("scala.collection.Seq")
    }.map {
      ScalaType.designator
    }

  def checkConformance(checkNames: Boolean,
                       parameters: Seq[Parameter],
                       exprs: Seq[Expression],
                       checkWithImplicits: Boolean)
                      (implicit project: ProjectContext): ConstraintsResult = {
    val r = checkConformanceExt(checkNames, parameters, exprs, checkWithImplicits, isShapesResolve = false)

    if (r.problems.nonEmpty) ConstraintsResult.Left
    else r.constraints
  }

  def clashedAssignmentsIn(exprs: Seq[Expression]): Seq[ScAssignment] = {
    val pairs = for(Expression(assignment @ ScAssignment.Named(name)) <- exprs) yield (name, assignment)
    val names = pairs.unzip._1
    val clashedNames = names.diff(names.distinct)
    pairs.filter(p => clashedNames.contains(p._1)).map(_._2)
  }

  case class ConformanceExtResult(problems: Seq[ApplicabilityProblem],
                                  constraints: ConstraintSystem,
                                  defaultParameterUsed: Boolean = false,
                                  matched: Seq[(Parameter, ScExpression, ScType)] = Seq())

  object ConformanceExtResult {
    def apply(problems: Seq[ApplicabilityProblem])(implicit project: ProjectContext): ConformanceExtResult =
      ConformanceExtResult(problems, ConstraintSystem.empty)
  }

  def collectSimpleProblems(exprs: Seq[Expression], parameters: Seq[Parameter]): Seq[ApplicabilityProblem] = {
    val problems = new ArrayBuffer[ApplicabilityProblem]()
    exprs.foldLeft(parameters) { (parameters, expression) =>
      if (expression.expr == null) parameters.tail
      else expression.expr match {
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
    problems
  }

  def checkConformanceExt(checkNames: Boolean,
                          parameters: Seq[Parameter],
                          exprs: Seq[Expression],
                          checkWithImplicits: Boolean,
                          isShapesResolve: Boolean)
                         (implicit project: ProjectContext): ConformanceExtResult = {
    ProgressManager.checkCanceled()
    var constraintAccumulator = ConstraintSystem.empty

    val clashedAssignments = clashedAssignmentsIn(exprs)

    if(clashedAssignments.nonEmpty) {
      val problems = clashedAssignments.map(ParameterSpecifiedMultipleTimes(_))
      return ConformanceExtResult(problems, constraintAccumulator)
    }

    //optimization:
    val hasRepeated = parameters.exists(_.isRepeated)
    val maxParams: Int = if(hasRepeated) scala.Int.MaxValue else parameters.length

    val excess = exprs.length - maxParams

    if (excess > 0) {
      val arguments = exprs.takeRight(excess).map(_.expr)
      return ConformanceExtResult(arguments.map(ExcessArgument), constraintAccumulator)
    }

    val minParams = parameters.count(p => !p.isDefault && !p.isRepeated)
    if (exprs.length < minParams) {
      return ConformanceExtResult(collectSimpleProblems(exprs, parameters), constraintAccumulator)
    }

    if (parameters.isEmpty)
      return ConformanceExtResult(if(exprs.isEmpty) Seq.empty else Seq(new ApplicabilityProblem("5")), constraintAccumulator)

    var k = 0
    var namedMode = false //todo: optimization, when namedMode enabled, exprs.length <= parameters.length
    val used = new Array[Boolean](parameters.length)
    var problems: List[ApplicabilityProblem] = Nil
    var matched: List[(Parameter, ScExpression, ScType)] = Nil
    var defaultParameterUsed = false

    def doNoNamed(expr: Expression): List[ApplicabilityProblem] = {
      if (namedMode) {
        List(PositionalAfterNamedArgument(expr.expr))
      }
      else {
        val getIt = used.indexOf(false)
        used(getIt) = true
        val param: Parameter = parameters(getIt)
        val paramType = param.paramType
        val expectedType = param.expectedType
        val typeResult =
          expr.getTypeAfterImplicitConversion(checkWithImplicits, isShapesResolve, Some(expectedType))._1
        typeResult.toOption match {
          case None => Nil
          case Some(exprType) =>
            val conforms = exprType.weakConforms(paramType)
            matched ::= (param, expr.expr, exprType)
            if (!conforms) List(TypeMismatch(expr.expr, paramType))
            else {
              constraintAccumulator += exprType.conforms(paramType, ConstraintSystem.empty, checkWeak = true).constraints
              List.empty
            }
        }
      }
    }

    while (k < parameters.length.min(exprs.length)) {
      exprs(k) match {
        case Expression(expr: ScTypedExpression) if expr.isSequenceArg =>
          seqTypeFor(expr) match {
            case Some(seqType) =>
              val getIt = used.indexOf(false)
              used(getIt) = true
              val param: Parameter = parameters(getIt)

              if (!param.isRepeated)
                problems ::= ExpansionForNonRepeatedParameter(expr)

              val tp = ScParameterizedType(seqType, Seq(param.paramType))
              val expectedType = ScParameterizedType(seqType, Seq(param.expectedType))

              for (exprType <- expr.getTypeAfterImplicitConversion(checkWithImplicits, isShapesResolve, Some(expectedType)).tr.toOption) {
                if (exprType.weakConforms(tp)) {
                  matched ::= (param, expr, exprType)
                  constraintAccumulator += exprType.conforms(tp, ConstraintSystem.empty, checkWeak = true).constraints
                } else {
                  return ConformanceExtResult(Seq(TypeMismatch(expr, tp)), constraintAccumulator, defaultParameterUsed, matched)
                }
              }
            case _ =>
              problems :::= doNoNamed(Expression(expr)).reverse
          }
        case Expression(assign@ScAssignment.Named(name)) =>
          val index = parameters.indexWhere { p =>
            ScalaNamesUtil.equivalent(p.name, name) ||
              p.deprecatedName.exists(ScalaNamesUtil.equivalent(_, name))
          }
          if (index == -1 || used(index)) {
            def extractExpression(assign: ScAssignment): ScExpression = {
              if (ScUnderScoreSectionUtil.isUnderscoreFunction(assign)) assign
              else assign.rightExpression.getOrElse(assign)
            }
            problems :::= doNoNamed(Expression(extractExpression(assign))).reverse
          } else {
            if (!checkNames)
              return ConformanceExtResult(Seq(new ApplicabilityProblem("9")), constraintAccumulator, defaultParameterUsed, matched)
            used(index) = true
            val param: Parameter = parameters(index)
            if (index != k) {
              namedMode = true
            }

            assign.rightExpression match {
              case rightExpression@Some(expr: ScExpression) =>
                val maybeSeqType = rightExpression.collect {
                  case typedStmt: ScTypedExpression if typedStmt.isSequenceArg => typedStmt
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
                    matched ::= (param, expr, exprType)
                    constraintAccumulator += exprType.conforms(paramType, ConstraintSystem.empty, checkWeak = true).constraints
                  } else {
                    problems ::= TypeMismatch(expr, paramType)
                  }
                }
              case _ =>
                return ConformanceExtResult(Seq(new ApplicabilityProblem("11")), constraintAccumulator, defaultParameterUsed, matched)
            }
          }
        case expr: Expression =>
          problems :::= doNoNamed(expr).reverse
      }
      k = k + 1
    }

    if (problems.nonEmpty) return ConformanceExtResult(problems.reverse, constraintAccumulator, defaultParameterUsed, matched)

    if (exprs.length == parameters.length) return ConformanceExtResult(Seq.empty, constraintAccumulator, defaultParameterUsed, matched)
    else if (exprs.length > parameters.length) {
      if (namedMode)
        return ConformanceExtResult(Seq(new ApplicabilityProblem("12")), constraintAccumulator, defaultParameterUsed, matched)
      if (!parameters.last.isRepeated)
        return ConformanceExtResult(Seq(new ApplicabilityProblem("13")), constraintAccumulator, defaultParameterUsed, matched)
      val paramType: ScType = parameters.last.paramType
      val expectedType: ScType = parameters.last.expectedType
      while (k < exprs.length) {
        for (exprType <- exprs(k).getTypeAfterImplicitConversion(checkWithImplicits, isShapesResolve, Some(expectedType))._1) {
          val conforms = exprType.weakConforms(paramType)
          if (!conforms) {
            return ConformanceExtResult(Seq(ElementApplicabilityProblem(exprs(k).expr, exprType, paramType)),
              constraintAccumulator, defaultParameterUsed, matched)
          } else {
            matched ::= (parameters.last, exprs(k).expr, exprType)
            constraintAccumulator += exprType.conforms(paramType, ConstraintSystem.empty, checkWeak = true).constraints
          }
        }
        k = k + 1
      }
    }
    else {
      if (exprs.length == parameters.length - 1 && !namedMode && parameters.last.isRepeated)
        return ConformanceExtResult(Seq.empty, constraintAccumulator, defaultParameterUsed, matched)

      val missed = for ((parameter: Parameter, b) <- parameters.zip(used)
                        if !b && !parameter.isDefault) yield MissedValueParameter(parameter)
      defaultParameterUsed = parameters.zip(used).exists { case (param, bool) => !bool && param.isDefault}
      if (missed.nonEmpty) return ConformanceExtResult(missed, constraintAccumulator, defaultParameterUsed, matched)
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
              matched ::= (param, expr, defaultTp)

              constraintAccumulator += defaultTp.conforms(paramType, ConstraintSystem.empty).constraints
            case Some(defaultTp) =>
                return ConformanceExtResult(Seq(DefaultTypeParameterMismatch(defaultTp, paramType)), constraintAccumulator,
                  defaultParameterUsed = true, matched)
            case _ =>
          }
        }
      }
    }
    ConformanceExtResult(Seq.empty, constraintAccumulator, defaultParameterUsed, matched)
  }

  def toParameter(p: ScParameter, substitutor: ScSubstitutor): Parameter = {
    val t = substitutor(p.`type`().getOrNothing)
    val default = p.getDefaultExpression.flatMap(_.`type`().toOption.map(substitutor))
    Parameter(p.name, p.deprecatedName, t, t, p.isDefaultParam, p.isRepeatedParameter, p.isCallByNameParameter,
      p.index, Some(p), default)
  }

  def toParameter(p: PsiParameter): Parameter = {
    val tp = p.paramType(exact = false)
    Parameter(if (p.isInstanceOf[ClsParameterImpl]) "" else p.name, None, tp, tp, false, p.isVarArgs, false, p.index,
      p match {
        case param: ScParameter => Some(param)
        case _ => None
      })
  }

  // TODO refactor a lot of duplication out of this method
  def compatible(named: PsiNamedElement,
                 substitutor: ScSubstitutor,
                 argClauses: List[Seq[Expression]],
                 checkWithImplicits: Boolean,
                 scope: GlobalSearchScope,
                 isShapesResolve: Boolean,
                 ref: PsiElement = null)
                (implicit project: ProjectContext): ConformanceExtResult = {
    def checkParameterListConformance(checkNames: Boolean, parameters: Seq[Parameter], arguments: Seq[Expression]) =
      checkConformanceExt(checkNames, parameters, arguments, checkWithImplicits, isShapesResolve)

    val firstArgumentListArgs: Seq[Expression] = argClauses.headOption.getOrElse(Seq.empty)

    named match {
      case synthetic: ScSyntheticFunction =>
        if (synthetic.paramClauses.isEmpty)
          return ConformanceExtResult(Seq(new DoesNotTakeParameters))

        val parameters = synthetic.paramClauses.head.map(p =>
          p.copy(paramType = substitutor(p.paramType))
        )

        checkParameterListConformance(checkNames = false, parameters, firstArgumentListArgs)
      case fun: ScFunction =>
        if(!fun.hasParameterClause && argClauses.nonEmpty)
          return ConformanceExtResult(Seq(new DoesNotTakeParameters))

        if (QuasiquoteInferUtil.isMetaQQ(fun) && ref.isInstanceOf[ScReferenceExpression]) {
          val params = QuasiquoteInferUtil.getMetaQQExpectedTypes(ref.asInstanceOf[ScReferenceExpression])
          return checkParameterListConformance(checkNames = false, params, firstArgumentListArgs)
        }

        val parameters =
          fun.effectiveParameterClauses
          .headOption
          .toList
          .flatMap(_.effectiveParameters)
          .map(toParameter(_, substitutor))

        checkParameterListConformance(checkNames = true, parameters, firstArgumentListArgs)
      case constructor: ScPrimaryConstructor =>
        val parameters = constructor.effectiveFirstParameterSection.map(toParameter(_, substitutor))
        checkParameterListConformance(checkNames = true, parameters, firstArgumentListArgs)
      case method: PsiMethod =>
        val parameters = method.parameters.map(param =>
          Parameter(substitutor(param.paramType()), isRepeated = param.isVarArgs, index = -1)
        )

        checkParameterListConformance(checkNames = false, parameters, firstArgumentListArgs)
      case _ =>
        ConformanceExtResult(Seq(new ApplicabilityProblem("22")))
    }
  }

  def checkConstructorConformance(constrInvocation: ScConstructor,
                                  substitutor: ScSubstitutor,
                                  argClauses: Seq[ScArgumentExprList],
                                  paramClauses: Seq[ScParameterClause])
                                 (implicit project: ProjectContext): ConformanceExtResult = {

    // a first empty argument clause might lack
    val nonEmptyArgClause =
      if (argClauses.isEmpty) Seq(Seq.empty)
      else argClauses.map(_.exprs)

    val (result, _) = nonEmptyArgClause.zip(paramClauses).foldLeft(ConformanceExtResult(Seq.empty) -> substitutor) {
      case ((prevRes, prevSubstitutor), (args, paramClause)) =>

        val params = paramClause.effectiveParameters.map(toParameter(_, prevSubstitutor))

        val argExprs = args.map(new Expression(_))
        val eligibleForAutoTupling = args.length != 1 && params.length == 1 && !params.head.isDefault

        val curRes = checkConformanceExt(checkNames = true, params, argExprs, checkWithImplicits = true, isShapesResolve = false) match {
          case res if eligibleForAutoTupling && res.problems.nonEmpty =>
            // try autotupling. If the conformance check succeeds without problems we use that result
            ScalaPsiUtil
              .tupled(args, constrInvocation.typeElement)
              .map(checkConformanceExt(checkNames = true, params, _, checkWithImplicits = true, isShapesResolve = true))
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
    var minParamClauses = paramClauses.length

    val hasImplicitClause = paramClauses.lastOption.exists(_.isImplicit)
    if (hasImplicitClause)
      minParamClauses -= 1

    if (nonEmptyArgClause.length < minParamClauses) {
      val missingClauses = paramClauses.drop(nonEmptyArgClause.length)
      result.copy(
        problems = result.problems ++ missingClauses.map(MissedParametersClause.apply)
      )
    } else {
      result
    }
  }
}
