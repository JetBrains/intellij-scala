package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, MacroInvocationContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames.Update
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor._
import org.jetbrains.plugins.scala.macroAnnotations.CachedWithRecursionGuard
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle}


/**
  * Nikolay.Tropin
  * 19-Dec-17
  */
abstract class MethodInvocationImpl(node: ASTNode) extends ScExpressionImplBase(node) with MethodInvocation {

  import MethodInvocationImpl._

  override protected def innerType: TypeResult = innerTypeExt.typeResult

  override def applicationProblems: Seq[ApplicabilityProblem] = innerTypeExt match {
    case RegularCase(_, problems, _)                           => problems
    case SyntheticCase(RegularCase(_, problems, _), _, _)      => problems
    case FailureCase(_, problems) if problems.nonEmpty         => problems
    case FailureCase(Failure(`noSuitableMethodFoundError`), _) => Seq(DoesNotTakeParameters())
    case _                                                     => Seq.empty
  }

  override protected def matchedParametersInner: Seq[(Parameter, ScExpression, ScType)] = innerTypeExt match {
    case RegularCase(_, _, matched) => matched
    case SyntheticCase(RegularCase(_, _, matched), _, _) => matched
    case _ => Seq.empty
  }

  override final def getImportsUsed: Set[ImportUsed] = innerTypeExt match {
    case syntheticCase: SyntheticCase => syntheticCase.resolveResult.importsUsed
    case _ => Set.empty
  }

  override final def getImplicitFunction: Option[ScalaResolveResult] = innerTypeExt match {
    case syntheticCase: SyntheticCase => syntheticCase.resolveResult.implicitConversion
    case _ => None
  }

  override final def applyOrUpdateElement: Option[ScalaResolveResult] = innerTypeExt match {
    case syntheticCase: SyntheticCase if syntheticCase.isApplyOrUpdate => Some(syntheticCase.resolveResult)
    case _ => None
  }

  //noinspection ScalaExtractStringToBundle
  @CachedWithRecursionGuard(this, FailureCase(Failure("Recursive innerTypeExt"), Seq.empty), BlockModificationTracker(this))
  private def innerTypeExt: InvocationData = try {
    tryToGetInnerTypeExt(useExpectedType = true)
  } catch {
    case _: SafeCheckException => tryToGetInnerTypeExt(useExpectedType = false)
  }

  //this method works for ScInfixExpression and ScMethodCall
  private def tryToGetInnerTypeExt(implicit useExpectedType: Boolean): InvocationData = {
    def updateImplicitParameters(regularCase: RegularCase, srr: Option[ScalaResolveResult]) = {
      val RegularCase(inferredType, problems, matched) = regularCase
      val (newType, arguments) = this.updatedWithImplicitParameters(inferredType, useExpectedType)
      setImplicitArguments(arguments)
      val actualType = srr.fold(newType)(widenEnumCaseCopyOrApplyMethod(newType, _))
      RegularCase(actualType, problems, matched)
    }

    def updateType(`type`: ScType, canThrowSCE: Boolean = false): ScType =
      if (useExpectedType)
        updateAccordingToExpectedType(`type`, filterTypeParams = false, this.expectedType(), this, canThrowSCE)
      else `type`

    getEffectiveInvokedExpr.getNonValueType() match {
      case Right(scType) =>
        val nonValueType = updateType(scType, canThrowSCE = true)

        val invokedResolveResult = getEffectiveInvokedExpr match {
          case ref: ScReferenceExpression => ref.bind()
          case _                          => None
        }

        checkApplication(nonValueType, invokedResolveResult) match {
          case Some(regularCase) => updateImplicitParameters(regularCase, invokedResolveResult)
          case _ =>
            val applyOrUpdateCandidates = this.findPossibleApplyOrUpdateCandidates(nonValueType)
            applyOrUpdateCandidates.collect { case Array(result) => this.updateGenericType(nonValueType, result) } match {
              case Some((processedType, result)) =>
                val updatedProcessedType = updateType(processedType)

                val maybeRegularCase = checkApplication(updatedProcessedType, Some(result))
                val regularCase = maybeRegularCase.getOrElse {
                  RegularCase(updatedProcessedType, Seq(new DoesNotTakeParameters))
                }

                SyntheticCase(
                  updateImplicitParameters(regularCase, invokedResolveResult),
                  result,
                  maybeRegularCase.isDefined
                )
              case _ =>
                val problems = applyOrUpdateCandidates
                  .getOrElse(Array.empty)
                  .flatMap(_.problems)
                FailureCase(Failure(noSuitableMethodFoundError), problems.toSeq)
            }
        }
      case left@Left(_) => FailureCase(left)
    }
  }

  private def tuplizyCase(
    expressions: Seq[Expression],
  )(function:    Seq[Expression] => (ScType, ConformanceExtResult)
  ): RegularCase = {
    def asRegularCase(expressions: Seq[Expression]): RegularCase = {
      val (tp, ConformanceExtResult(problems, _, _, matched)) = function(expressions)
      RegularCase(tp, problems, matched)
    }

    def tupledWithSubstitutedType =
      tupled(expressions, this)
        .map(asRegularCase)
        .flatMap(_.withSubstitutedType)

    val nonTupled = asRegularCase(expressions)
    nonTupled.withSubstitutedType
      .orElse(tupledWithSubstitutedType)
      .getOrElse(nonTupled)
  }

  private def replaceLastComponent(in: ScType, replaceWith: ScType => ScType): ScType = in match {
    case FunctionType(res, args) => FunctionType((replaceLastComponent(res, replaceWith), args))
    case t                       => replaceWith(t)
  }

  /**
   * If method resolves to synthetic copy/apply method of an enum case,
   * widen its return type to the underlying type as long as it is
   * compatible with expected type.
   */
  private def widenEnumCaseCopyOrApplyMethod(
    tpe:       ScType,
    methodSrr: ScalaResolveResult
  ): ScType =
    methodSrr match {
      case ScalaResolveResult(method: ScFunctionDefinition, _) if method.isSynthetic =>
        val cls = method.containingClass.asInstanceOf[ScTypeDefinition]

        val isEnumCaseApplyMethod =
          method.isApplyMethod &&
            (cls match {
              case obj: ScObject =>
                ScalaPsiUtil.getCompanionModule(obj).exists(ScEnumCase.isDesugaredEnumCase)
              case _ => false
            })

        val isEnumCaseCopyMethod =
          method.isCopyMethod && ScEnumCase.isDesugaredEnumCase(cls)

        if (isEnumCaseApplyMethod || isEnumCaseCopyMethod) {
          val widened = replaceLastComponent(tpe.inferValueType, _.widenToParent)

          if (this.expectedType().forall(widened.conforms)) widened
          else                                              tpe
        } else tpe
      case _ => tpe
    }

  private def checkApplication(invokedNonValueType: ScType,
                               maybeResolveResult: Option[ScalaResolveResult])
                              (implicit useExpectedType: Boolean): Option[RegularCase] = {
    val fromMacroExpansion =
      maybeResolveResult
        .flatMap(res => this.checkMacro(res).orElse(this.checkMacroExpansion(res)))
        .map(RegularCase(_))

    if (fromMacroExpansion.isDefined) return fromMacroExpansion

    val maybeTuple = invokedNonValueType match {
      case pTpe @ ScTypePolymorphicType(ScMethodType(returnType, parameters, _), _) =>
        Some((returnType, parameters, Some(pTpe)))
      case pTpe @ ScTypePolymorphicType(FunctionTypeParameters(returnType, parameters), _) =>
        Some((returnType, parameters, Some(pTpe)))
      case ScMethodType(returnType, parameters, _) =>
        Some((returnType, parameters, None))
      case _ => None
    }

    maybeTuple.map {
      case (returnType, parameters, maybePolymorphicType) =>
        val function: Seq[Expression] => (ScType, ConformanceExtResult) = maybePolymorphicType match {
          case Some(polymorphicType) =>
            val canThrowSCE = useExpectedType && this.expectedType().isDefined /* optimization to avoid except */

            val paramSubst = canThrowSCE.option(
              polymorphicType.argsProtoTypeSubst(this.expectedType().get)
            )

            localTypeInferenceWithApplicabilityExt(
              returnType,
              parameters,
              _: Seq[Expression],
              polymorphicType.typeParameters,
              canThrowSCE = canThrowSCE,
              paramSubst  = paramSubst
            )
          case _ =>
            (expressions: Seq[Expression]) => {
              val conformanceResult = checkConformanceExt(
                parameters,
                expressions,
                checkWithImplicits = true,
                isShapesResolve    = false
              )

              (returnType, conformanceResult)
            }
        }

        tuplizyCase(arguments(maybeResolveResult))(function)
    }
  }

  private def arguments(maybeResolveResult: Option[ScalaResolveResult])
                       (implicit elementScope: ElementScope): Seq[Expression] = {
    val updateArgument = maybeResolveResult
      .find(_.name == Update)
      .map(_ => getContext)
      .collect {
        case ScAssignment(call: ScMethodCall, Some(right)) if call == this => right
      }

    argumentExpressions ++ updateArgument match {
      case arguments if maybeResolveResult.exists(isApplyDynamicNamed) =>
        arguments.collect {
          case ScAssignment(left: ScReferenceExpression, Some(right)) if left.qualifier.isEmpty => right
          case argument => argument
        }.map { expr =>
            (
              checkImplicits:  Boolean,
              isShape:         Boolean,
              expectedOption:  Option[ScType],
              ignoreBaseTypes: Boolean,
              fromUnderscore:  Boolean
            ) =>
              {
                expr.getTypeAfterImplicitConversion(
                  checkImplicits,
                  isShape,
                  expectedOption.map {
                    case SecondType(t) => t
                    case t             => t
                  }
                ) match {
                  case ExpressionTypeResult(typeResult, imports, _) =>
                    ExpressionTypeResult(typeResult.map(SecondType(_)), imports)
                }
              }
        }
      case arguments => arguments
    }
  }
}

object MethodInvocationImpl {
  private val noSuitableMethodFoundError: NlsString = ScalaBundle.nls("suitable.method.not.found")

  private object FunctionTypeParameters {

    def unapply(`type`: ScType)
               (implicit elementScope: ElementScope): Option[(ScType, Seq[Parameter])] = `type` match {
      case FunctionType(returnType, types) =>
        elementScope.getFunctionTrait(types.length)
          .flatMap(_.functions.find(_.isApplyMethod))
          .map { applyFunction =>
            (returnType, parameters(applyFunction, types))
          }
      case _ => None
    }

    private def parameters(applyFunction: ScFunction, types: Seq[ScType]): Seq[Parameter] =
      applyFunction.parameters.zip(types).mapWithIndex {
        case ((parameter, tp), i) =>
          Parameter("v" + (i + 1), None, tp, tp, index = i, psiParam = Some(parameter))
      }
  }

  private object SecondType {

    def apply(`type`: ScType)
             (implicit elementScope: ElementScope): ScType = {
      val maybeStringType = elementScope.getCachedClass("java.lang.String")
        .map(ScalaType.designator(_))

      api.TupleType(Seq(maybeStringType.getOrElse(api.Any), `type`))
    }

    def unapply(`type`: ScType): Option[ScType] = `type` match {
      case api.TupleType(Seq(_, result)) => Some(result)
      case _ => None
    }
  }

  private implicit class MethodInvocationExt(private val invocation: MethodInvocationImpl) extends AnyVal {

    def checkMacroExpansion(result: ScalaResolveResult): Option[ScType] =
      ScalaMacroEvaluator.getInstance(invocation.getProject)
        .expandMacro(result.element, MacroInvocationContext(invocation, result))
        .flatMap(_.getNonValueType().toOption)

    def checkMacro(result: ScalaResolveResult): Option[ScType] =
      ScalaMacroEvaluator
        .getInstance(invocation.getProject)
        .checkMacro(
          result.element,
          MacroContext(invocation.getContext, invocation.expectedType()))

    def findPossibleApplyOrUpdateCandidates(`type`: ScType): Option[Array[ScalaResolveResult]] = {
      def findApplyOrUpdate(isDynamic: Boolean) =
        processTypeForUpdateOrApplyCandidates(invocation, `type`, isShape = false, isDynamic = isDynamic) match {
          case Array() => None
          case results if results.forall(_.element.isInstanceOf[PsiMethod]) => Some(results)
          case _ => None
        }

      findApplyOrUpdate(isDynamic = false)
        .orElse(findApplyOrUpdate(isDynamic = true))
    }

    def updateGenericType(`type`: ScType, resolveResult: ScalaResolveResult): (ScType, ScalaResolveResult) = {
      val updatedType = (`type`, polymorphicType(resolveResult)) match {
        case (ScTypePolymorphicType(_, head), ScTypePolymorphicType(internal, tail)) =>
          removeBadBounds(ScTypePolymorphicType(internal, head ++ tail))
        case (ScTypePolymorphicType(_, head), internalType) => ScTypePolymorphicType(internalType, head)
        case (_, polymorphicType) => polymorphicType
      }

      (updatedType, resolveResult)
    }

    private def polymorphicType(result: ScalaResolveResult) =
      result.element.asInstanceOf[PsiMethod]
        .methodTypeProvider(invocation.elementScope)
        .polymorphicType(result.substitutor)
        .updateTypeOfDynamicCall(result.isDynamic)
  }

  private sealed trait InvocationData {
    def typeResult: TypeResult
  }

  private case class RegularCase(
    inferredType: ScType,
    problems:     Seq[ApplicabilityProblem] = Seq.empty,
    matched:      Seq[(Parameter, ScExpression, ScType)] = Seq.empty
  ) extends InvocationData {

    override def typeResult: TypeResult = Right(inferredType)

    def withSubstitutedType: Option[RegularCase] = (problems, matched) match {
      case (Seq(), Seq()) => Some(this)
      case (Seq(), matchedParams) =>
        val paramSubstitutor = ScSubstitutor.paramToType(matchedParams.map(_._1), matchedParams.map(_._3))
        val `type`           = paramSubstitutor(inferredType)
        Some(RegularCase(`type`, Seq.empty, matched))
      case _ => None
    }
  }

  private case class SyntheticCase(full: RegularCase, resolveResult: ScalaResolveResult, isApplyOrUpdate: Boolean)
      extends InvocationData {
    override def typeResult: TypeResult = full.typeResult
  }

  private case class FailureCase(
    override val typeResult: Left[Failure, ScType],
    problems:                Seq[ApplicabilityProblem] = Seq.empty
  ) extends InvocationData

}