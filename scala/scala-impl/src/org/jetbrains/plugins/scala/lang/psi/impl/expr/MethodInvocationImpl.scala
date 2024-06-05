package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiMethod, PsiTypeParameterListOwner}
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, MacroInvocationContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumClassCase, ScFun, ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, ParameterizedType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.ScExpressionForExpectedTypesEx
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor._
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle}

import scala.annotation.tailrec

abstract class MethodInvocationImpl(node: ASTNode) extends ScExpressionImplBase(node) with MethodInvocation {

  import MethodInvocationImpl._

  override protected def innerType: TypeResult = innerTypeExt.typeResult

  override def target: Option[ScalaResolveResult] = innerTypeExt.target

  override def applicationProblems: Seq[ApplicabilityProblem] = innerTypeExt match {
    case RegularCase(_, _, problems, _)                        => problems
    case SyntheticCase(RegularCase(_, _, problems, _), _, _)   => problems
    case FailureCase(_, problems) if problems.nonEmpty         => problems
    case FailureCase(Failure(`noSuitableMethodFoundError`), _) => Seq(DoesNotTakeParameters)
    case _                                                     => Seq.empty
  }

  override protected def matchedParametersInner: Seq[(Parameter, ScExpression, ScType)] = innerTypeExt match {
    case RegularCase(_, _, _, matched) => matched
    case SyntheticCase(RegularCase(_, _, _, matched), _, _) => matched
    case _ => Seq.empty
  }

  override final def getImplicitFunction: Option[ScalaResolveResult] =
    applyOrUpdateElement.flatMap(_.implicitConversion)

  override final def getImportsUsed: Set[ImportUsed] = applyOrUpdateElement match {
    case Some(srr) => srr.importsUsed
    case None      => Set.empty
  }

  override final def applyOrUpdateElement: Option[ScalaResolveResult] = innerTypeExt match {
    case syntheticCase: SyntheticCase if syntheticCase.isApplyOrUpdate => Some(syntheticCase.resolveResult)
    case _ => None
  }

  //noinspection ScalaExtractStringToBundle
  private def innerTypeExt: InvocationData = cachedWithRecursionGuard("innerTypeExt", this, FailureCase(Failure("Recursive innerTypeExt"), Seq.empty): InvocationData, BlockModificationTracker(this)) {
    try {
      tryToGetInnerTypeExt(useExpectedType = true)
    } catch {
      case _: SafeCheckException => tryToGetInnerTypeExt(useExpectedType = false)
    }
  }

  //this method works for ScInfixExpression and ScMethodCall
  private def tryToGetInnerTypeExt(implicit useExpectedType: Boolean): InvocationData = {
    def updateImplicitParameters(regularCase: RegularCase, srr: Option[ScalaResolveResult]) = {
      val RegularCase(inferredType, target, problems, matched) = regularCase
      val (newType, arguments) = this.updatedWithImplicitParameters(inferredType, useExpectedType)
      setImplicitArguments(arguments)
      val actualType = srr.fold(newType)(widenEnumCaseCopyOrApplyMethod(newType, _))
      RegularCase(actualType, target, problems, matched)
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
            val stripTypeArgs =
              getEffectiveInvokedExpr match {
                case gen: ScGenericCall =>
                  gen.bindInvokedExpr.forall(ApplyOrUpdateInvocation.innerSrrHasTypeParameters)
                case _                  => true
              }

            val applyOrUpdateCands = this.resolveApplyMethod(
              this,
              nonValueType,
              shapesOnly    = false,
              stripTypeArgs = stripTypeArgs,
              withImplicits = true
            )

            applyOrUpdateCands match {
              case Array(srr) =>
                val processedType = this.updateGenericType(nonValueType, srr).updateTypeOfDynamicCall(srr.isDynamic)
                val updatedProcessedType = updateType(processedType)

                val maybeRegularCase = checkApplication(updatedProcessedType, srr.toOption)
                val regularCase = maybeRegularCase.getOrElse {
                  RegularCase(updatedProcessedType, srr.toOption, Seq(DoesNotTakeParameters))
                }

                SyntheticCase(
                  updateImplicitParameters(regularCase, invokedResolveResult),
                  srr,
                  maybeRegularCase.isDefined
                )
              case _ =>
                val problems = applyOrUpdateCands.flatMap(_.problems)
                FailureCase(Failure(noSuitableMethodFoundError), problems.toSeq)
            }
        }
      case left@Left(_) => FailureCase(left)
    }
  }

  private def tuplizyCase(
    expressions:        Seq[Expression],
    maybeResolveResult: Option[ScalaResolveResult],
  )(function:           Seq[Expression] => (ScType, ApplicabilityCheckResult)
  ): RegularCase = {
    def asRegularCase(expressions: Seq[Expression]): RegularCase = {
      val (tp, ApplicabilityCheckResult(problems, _, _, matched)) = function(expressions)
      RegularCase(tp, maybeResolveResult, problems, matched)
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

  private def widenToDirectParents(tpe: ScType): ScType = {
    @tailrec
    def directParents(tpe: ScType): Seq[ScType] = tpe match {
      case ParameterizedType(des, args) =>
        des match {
          case DesignatorOwner(tdef: ScTemplateDefinition) =>
            val subst = ScSubstitutor.bind(tdef.getTypeParameters, args)
            tdef.superTypes.map(subst)
          case _ => Seq.empty
        }
      case DesignatorOwner(tdef: ScTemplateDefinition) =>
        tdef.superTypes
      case ScProjectionType(projected, _) =>
        directParents(projected)
      case _ => Seq.empty
    }

    val parentTypes = directParents(tpe)

    if (parentTypes.isEmpty)        tpe
    else if (parentTypes.size == 1) parentTypes.head
    else                            ScCompoundType(parentTypes)(tpe.projectContext)
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

        if ((method.isApplyMethod || method.isCopyMethod) && ScalaPsiUtil.getCompanionModule(cls).exists(_.is[ScEnumClassCase])) {
          val widened = replaceLastComponent(tpe.inferValueType, widenToDirectParents)

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
        .map(RegularCase(_, maybeResolveResult))

    if (fromMacroExpansion.isDefined) return fromMacroExpansion

    def resolveResultIsPostfixOrPrefixFunction = maybeResolveResult.exists {
      rr =>
        val e = rr.element

        def isFun = e.isInstanceOf[ScFun] || e.is[ScFunction]

        def isPostfixOrPrefix = isInstanceOf[ScPrefixExpr] || isInstanceOf[ScPostfixExpr]

        isFun && isPostfixOrPrefix
    }

    val maybeTuple = invokedNonValueType match {
      case pTpe @ ScTypePolymorphicType(ScMethodType(returnType, parameters, _), _) =>
        Option((returnType, parameters, Option(pTpe)))
      case pTpe @ ScTypePolymorphicType(FunctionTypeParameters(returnType, parameters), _) =>
        Option((returnType, parameters, Option(pTpe)))
      case ScMethodType(returnType, parameters, _)      => Option((returnType, parameters, None))
      case ty if resolveResultIsPostfixOrPrefixFunction => Option((ty, Seq.empty, None))
      case _ => None
    }

    maybeTuple.map {
      case (returnType, parameters, maybePolymorphicType) =>
        val function: Seq[Expression] => (ScType, ApplicabilityCheckResult) = maybePolymorphicType match {
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
              val conformanceResult = checkMethodApplicability(
                parameters,
                expressions,
                withImplicits = true,
                isOverloaded  = false
              )

              (returnType, conformanceResult)
            }
        }

        val args = maybeResolveResult.fold(argumentExpressions: Seq[Expression])(arguments)
        tuplizyCase(args, maybeResolveResult)(function)
    }
  }

  private def arguments(srr: ScalaResolveResult)
                       (implicit elementScope: ElementScope): Seq[Expression] = {
    val updateArgument =
      if (srr.name == CommonNames.Update)
        getContext match {
          case ScAssignment(call: ScMethodCall, Some(right)) if call == this => Seq(right)
          case _                                                             => Seq.empty
        }
      else Seq.empty

    argumentExpressions ++ updateArgument match {
      case arguments if isApplyDynamicNamed(srr) =>
        arguments.collect {
          case ScAssignment(left: ScReferenceExpression, Some(right)) if left.qualifier.isEmpty => right
          case argument                                                                         => argument
        }.map { expr =>
          (
            checkImplicits:  Boolean,
            isShape:         Boolean,
            expectedOption:  Option[ScType],
            _:               Boolean,
            _:               Boolean
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
  private val noSuitableMethodFoundError: NlsString = NlsString(ScalaBundle.message("suitable.method.not.found"))

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

    def updateGenericType(`type`: ScType, resolveResult: ScalaResolveResult): ScType =
      (`type`, polymorphicType(resolveResult)) match {
        case (ScTypePolymorphicType(_, head), ScTypePolymorphicType(internal, tail)) =>
          removeBadBounds(ScTypePolymorphicType(internal, head ++ tail))
        case (ScTypePolymorphicType(_, head), internalType) => ScTypePolymorphicType(internalType, head)
        case (_, polymorphicType)                           => polymorphicType
      }

    private def polymorphicType(result: ScalaResolveResult): ScType = {
      val dropExtensionClauses =
        result.isExtensionCall ||
          (result.extensionContext.nonEmpty &&
            result.element.asOptionOf[ScFunction].flatMap(_.extensionMethodOwner) == result.extensionContext)

      result.element.asInstanceOf[PsiMethod]
        .methodTypeProvider(invocation.elementScope)
        .polymorphicType(result.substitutor, dropExtensionClauses = dropExtensionClauses)
    }
  }

  private sealed trait InvocationData {
    def target: Option[ScalaResolveResult]
    def typeResult: TypeResult
  }

  private case class RegularCase(
    inferredType:        ScType,
    override val target: Option[ScalaResolveResult],
    problems:            Seq[ApplicabilityProblem] = Seq.empty,
    matched:             Seq[(Parameter, ScExpression, ScType)] = Seq.empty
  ) extends InvocationData {

    override def typeResult: TypeResult = Right(inferredType)

    def withSubstitutedType: Option[RegularCase] = (problems, matched) match {
      case (Seq(), Seq()) => Some(this)
      case (Seq(), matchedParams) =>
        val paramSubstitutor = ScSubstitutor.paramToType(matchedParams.map(_._1), matchedParams.map(_._3))
        val `type`           = paramSubstitutor(inferredType)
        Some(RegularCase(`type`, target, Seq.empty, matched))
      case _ => None
    }
  }

  private case class SyntheticCase(full: RegularCase, resolveResult: ScalaResolveResult, isApplyOrUpdate: Boolean)
    extends InvocationData {
    override def target: Option[ScalaResolveResult] = Some(resolveResult)
    override def typeResult: TypeResult = full.typeResult
  }

  private case class FailureCase(
    override val typeResult: TypeResult,
    problems:                Seq[ApplicabilityProblem] = Seq.empty
  ) extends InvocationData {
    override def target: Option[ScalaResolveResult] = None
  }
}
