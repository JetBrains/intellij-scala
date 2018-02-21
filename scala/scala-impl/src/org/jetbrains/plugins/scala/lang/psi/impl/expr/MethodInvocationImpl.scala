package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroInvocationContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor._
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}


/**
  * Nikolay.Tropin
  * 19-Dec-17
  */
abstract class MethodInvocationImpl(node: ASTNode) extends ScExpressionImplBase(node) with MethodInvocation {

  import MethodInvocationImpl._

  override protected def innerType: TypeResult = innerTypeExt.typeResult

  override def applicationProblems: Seq[ApplicabilityProblem] = innerTypeExt match {
    case RegularCase(_, problems, _) => problems
    case SyntheticCase(RegularCase(_, problems, _), _, _) => problems
    case _ => Seq.empty
  }

  override protected def matchedParametersInner: Seq[(Parameter, ScExpression, ScType)] = innerTypeExt match {
    case RegularCase(_, _, matched) => matched
    case SyntheticCase(RegularCase(_, _, matched), _, _) => matched
    case _ => Seq.empty
  }

  override final def getImportsUsed: Set[ImportUsed] = innerTypeExt match {
    case syntheticCase: SyntheticCase => syntheticCase.resolveResult.importsUsed.toSet
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

  @Cached(ModCount.getBlockModificationCount, this)
  private def innerTypeExt: InvocationData = try {
    tryToGetInnerTypeExt(useExpectedType = true)
  } catch {
    case _: SafeCheckException => tryToGetInnerTypeExt(useExpectedType = false)
  }

  //this method works for ScInfixExpression and ScMethodCall
  private def tryToGetInnerTypeExt(implicit useExpectedType: Boolean): InvocationData = {
    def updateImplicitParameters(regularCase: RegularCase) = {
      val RegularCase(inferredType, problems, matched) = regularCase
      val (newType, parameters) = this.updatedWithImplicitParameters(inferredType, useExpectedType)
      setImplicitParameters(parameters)

      RegularCase(newType, problems, matched)
    }

    def updateType(`type`: ScType, canThrowSCE: Boolean = false): ScType =
      if (useExpectedType) updateAccordingToExpectedType(`type`, fromImplicitSearch = false, filterTypeParams = false, this.expectedType(), this, canThrowSCE)
      else `type`

    getEffectiveInvokedExpr.getNonValueType() match {
      case Right(scType) =>
        val nonValueType = updateType(scType, canThrowSCE = true)
        val invokedResolveResult = getEffectiveInvokedExpr match {
          case ref: ScReferenceExpression => ref.bind()
          case _ => None
        }

        checkApplication(nonValueType, invokedResolveResult) match {
          case Some(regularCase) => updateImplicitParameters(regularCase)
          case _ =>
            this.findApplyUpdateOrDynamic(nonValueType) match {
              case Some((processedType, result)) =>
                val updatedProcessedType = updateType(processedType)

                val maybeRegularCase = checkApplication(updatedProcessedType, Some(result))
                val regularCase = maybeRegularCase.getOrElse {
                  RegularCase(updatedProcessedType, Seq(new DoesNotTakeParameters))
                }

                SyntheticCase(
                  updateImplicitParameters(regularCase),
                  result,
                  maybeRegularCase.isDefined
                )
              case _ => FailureCase(Failure("Suitable method not found"))
            }
        }
      case left@Left(_) => FailureCase(left)
    }
  }

  private def tuplizyCase(expressions: Seq[Expression])
                         (function: Seq[Expression] => (ScType, ConformanceExtResult)): RegularCase = {
    def asRegularCase(expressions: Seq[Expression]) = {
      val (tp, ConformanceExtResult(problems, _, _, matched)) = function(expressions)
      RegularCase(tp, problems, matched)
    }

    def tupledWithSubstitutedType = tupled(expressions, this)
      .map(asRegularCase)
      .flatMap(_.withSubstitutedType)

    val nonTupled = asRegularCase(expressions)
    nonTupled.withSubstitutedType
      .orElse(tupledWithSubstitutedType)
      .getOrElse(nonTupled)
  }

  private def checkApplication(invokedNonValueType: ScType,
                               maybeResolveResult: Option[ScalaResolveResult])
                              (implicit useExpectedType: Boolean): Option[RegularCase] = {
    val fromMacroExpansion = maybeResolveResult
      .flatMap(this.checkMacroExpansion)
      .map(RegularCase(_))
    if (fromMacroExpansion.isDefined) return fromMacroExpansion

    val maybeTuple = invokedNonValueType match {
      case polymorphicType@ScTypePolymorphicType(ScMethodType(returnType, parameters, _), _) =>
        Some((returnType, parameters, Some(polymorphicType)))
      case polymorphicType@ScTypePolymorphicType(FunctionTypeParameters(returnType, parameters), _) =>
        Some((returnType, parameters, Some(polymorphicType)))
      case ScMethodType(returnType, parameters, _) =>
        Some((returnType, parameters, None))
      case _ => None
    }

    maybeTuple.map {
      case (returnType, parameters, maybePolymorphicType) =>
        val function = maybePolymorphicType match {
          case Some(polymorphicType) =>
            val canThrowSCE = useExpectedType && this.expectedType().isDefined /* optimization to avoid except */
            localTypeInferenceWithApplicabilityExt(returnType, parameters, _: Seq[Expression], polymorphicType.typeParameters, canThrowSCE = canThrowSCE)
          case _ =>
            (expressions: Seq[Expression]) => (returnType, checkConformanceExt(checkNames = true, parameters, expressions, checkWithImplicits = true, isShapesResolve = false))
        }

        tuplizyCase(arguments(maybeResolveResult))(function)
    }
  }

  private def arguments(maybeResolveResult: Option[ScalaResolveResult])
                       (implicit elementScope: ElementScope): Seq[Expression] = {
    val updateArgument = maybeResolveResult
      .find(_.name == ScFunction.Ext.Update)
      .map(_ => getContext)
      .collect {
        case ScAssignStmt(call: ScMethodCall, Some(right)) if call == this => right
      }

    argumentExpressions ++ updateArgument match {
      case arguments if maybeResolveResult.exists(isApplyDynamicNamed) =>
        arguments.collect {
          case ScAssignStmt(left: ScReferenceExpression, Some(right)) if left.qualifier.isEmpty => right
          case argument => argument
        }.map {
          new Expression(_) {

            override def getTypeAfterImplicitConversion(checkImplicits: Boolean, isShape: Boolean,
                                                        expectedOption: Option[ScType]): (TypeResult, collection.Set[ImportUsed]) =
              super.getTypeAfterImplicitConversion(
                checkImplicits,
                isShape,
                expectedOption.map {
                  case SecondType(t) => t
                  case t => t
                }
              ) match {
                case (typeResult, imports) => (typeResult.map(SecondType(_)), imports)
              }
          }
        }
      case arguments => arguments
    }
  }
}

object MethodInvocationImpl {

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

    private def parameters(applyFunction: ScFunction, types: Seq[ScType]) =
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

    def findApplyUpdateOrDynamic(`type`: ScType): Option[(ScType, ScalaResolveResult)] = {
      def findApplyUpdateOrDynamic(isDynamic: Boolean) =
        processTypeForUpdateOrApplyCandidates(invocation, `type`, isShape = false, isDynamic = isDynamic) match {
          case Array(result) if result.element.isInstanceOf[PsiMethod] => Some(result)
          case _ => None
        }

      findApplyUpdateOrDynamic(isDynamic = false)
        .orElse(findApplyUpdateOrDynamic(isDynamic = true))
        .map { result =>
          val updatedType = (`type`, polymorphicType(result)) match {
            case (ScTypePolymorphicType(_, head), ScTypePolymorphicType(internal, tail)) =>
              removeBadBounds(ScTypePolymorphicType(internal, head ++ tail))
            case (ScTypePolymorphicType(_, head), internalType) => ScTypePolymorphicType(internalType, head)
            case (_, polymorphicType) => polymorphicType
          }

          (updatedType, result)
        }
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

  private case class RegularCase(inferredType: ScType,
                                 problems: Seq[ApplicabilityProblem] = Seq.empty,
                                 matched: Seq[(Parameter, ScExpression, ScType)] = Seq.empty) extends InvocationData {

    override def typeResult: TypeResult = Right(inferredType)

    def withSubstitutedType: Option[RegularCase] = (problems, matched) match {
      case (Seq(), Seq()) => Some(this)
      case (Seq(), seq) =>
        val map = seq.collect {
          case (parameter, _, scType) => parameter -> scType
        }.toMap
        val `type` = ScSubstitutor(() => map).subst(inferredType)
        Some(RegularCase(`type`, matched = seq))
      case _ => None
    }
  }

  private case class SyntheticCase(full: RegularCase,
                                   resolveResult: ScalaResolveResult,
                                   isApplyOrUpdate: Boolean) extends InvocationData {
    override def typeResult: TypeResult = full.typeResult
  }

  private case class FailureCase(typeResult: Left[Failure, ScType]) extends InvocationData

}