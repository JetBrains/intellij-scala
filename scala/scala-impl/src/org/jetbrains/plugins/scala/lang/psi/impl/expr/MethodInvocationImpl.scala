package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroInvocationContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ExpectedTypesImpl._
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, FunctionType, TupleType, TypeParameter}
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
    case syntheticCase: SyntheticCase => syntheticCase.applyOrUpdate
    case _ => None
  }

  @Cached(ModCount.getBlockModificationCount, this)
  private def innerTypeExt: InvocationData = try {
    tryToGetInnerTypeExt(useExpectedType = true)
  } catch {
    case _: SafeCheckException => tryToGetInnerTypeExt(useExpectedType = false)
  }

  //this method works for ScInfixExpression and ScMethodCall
  private def tryToGetInnerTypeExt(useExpectedType: Boolean): InvocationData = {
    def update(regularCase: RegularCase) = {
      val RegularCase(inferredType, problems, matched) = regularCase
      val (newType, params) = this.updatedWithImplicitParameters(inferredType, useExpectedType)
      setImplicitParameters(params)

      RegularCase(newType, problems, matched)
    }

    getEffectiveInvokedExpr.getNonValueType() match {
      case Right(scType) =>
        val nonValueType = scType.updateAccordingToExpectedType(useExpectedType, this, canThrowSCE = true)
        checkApplication(nonValueType, useExpectedType, invokedResolveResult) match {
          case Some(regularCase) => update(regularCase)
          case _ =>
            checkApplyUpdateOrDynamic(nonValueType, useExpectedType) match {
              case Some(SyntheticCase(full, resolveResult, applyOrUpdate)) => SyntheticCase(update(full), resolveResult, applyOrUpdate)
              case None => FailureCase(Failure("Suitable method not found"))
            }
        }
      case left@Left(_) => FailureCase(left)
    }
  }

  private def tuplizyCase(exprs: Seq[Expression])
                         (fun: Seq[Expression] => (ScType, ConformanceExtResult)): RegularCase = {
    def asRegularCase(expressions: Seq[Expression]) = {
      val (tp, ConformanceExtResult(problems, _, _, matched)) = fun(expressions)
      RegularCase(tp, problems, matched)
    }

    def tupledWithSubstitutedType = tupled(exprs, this)
      .map(asRegularCase)
      .flatMap(_.withSubstitutedType)

    val nonTupled = asRegularCase(exprs)
    nonTupled.withSubstitutedType
      .orElse(tupledWithSubstitutedType)
      .getOrElse(nonTupled)
  }

  private def checkConformance(retType: ScType,
                               psiExprs: Seq[Expression],
                               parameters: Seq[Parameter]): RegularCase = tuplizyCase(psiExprs) { t =>
    (retType, checkConformanceExt(checkNames = true, parameters, exprs = t, checkWithImplicits = true, isShapesResolve = false))
  }

  private def checkConformanceWithInference(retType: ScType,
                                            withExpectedType: Boolean,
                                            psiExprs: Seq[Expression],
                                            typeParams: Seq[TypeParameter],
                                            parameters: Seq[Parameter]): RegularCase = tuplizyCase(psiExprs) {
    localTypeInferenceWithApplicabilityExt(retType, parameters, _, typeParams, canThrowSCE = withExpectedType)
  }

  private def checkApplication(invokedNonValueType: ScType,
                               useExpectedType: Boolean,
                               resolveResult: Option[ScalaResolveResult]): Option[RegularCase] = {

    val fromMacroExpansion = resolveResult.flatMap(checkMacroExpansion)
    if (fromMacroExpansion.nonEmpty) return fromMacroExpansion

    val includeUpdate = resolveResult.exists(_.name == "update")
    val isNamedDynamic = resolveResult.exists(isApplyDynamicNamed)

    val withExpectedType = useExpectedType && this.expectedType().isDefined //optimization to avoid except

    val args = arguments(includeUpdate, isNamedDynamic)

    invokedNonValueType match {
      case ScMethodType(retType, params, _) =>
        Some(checkConformance(retType, args, params))
      case ScTypePolymorphicType(ScMethodType(retType, params, _), typeParams) =>
        Some(checkConformanceWithInference(retType, withExpectedType, args, typeParams, params))
      case ScTypePolymorphicType(FunctionType(retType, params), typeParams) =>
        Some(checkConformanceWithInference(retType, withExpectedType, args, typeParams, functionParams(params)))
      case any if isSAMEnabled(this) =>
        toSAMType(any, this) match {
          case Some(FunctionType(retType: ScType, params: Seq[ScType])) =>
            Some(checkConformance(retType, args, functionParams(params)))
          case _ => None
        }
      case _ => None
    }
  }

  private def checkMacroExpansion(result: ScalaResolveResult): Option[RegularCase] =
    ScalaMacroEvaluator.getInstance(getProject)
      .expandMacro(result.element, MacroInvocationContext(this, result))
      .flatMap(_.getNonValueType().toOption)
      .map(RegularCase(_))

  private def checkApplyUpdateOrDynamic(invokedType: ScType, useExpectedType: Boolean): Option[SyntheticCase] =
    findApplyUpdateOrDynamic(invokedType, useExpectedType, isDynamic = false)
      .orElse(findApplyUpdateOrDynamic(invokedType, useExpectedType, isDynamic = true))

  private def functionParams(params: Seq[ScType]): Seq[Parameter] = {
    val functionClass = elementScope
      .getCachedClass(FunctionType.TypeName + params.length)
      .collect {
        case t: ScTrait => t
      }

    val applyFunction = functionClass.flatMap(_.functions.find(_.isApplyMethod))
    params.mapWithIndex {
      case (tp, i) =>
        new Parameter("v" + (i + 1), None, tp, tp, false, false, false, i, applyFunction.map(_.parameters(i)))
    }
  }

  private def arguments(includeUpdateCall: Boolean = false, isNamedDynamic: Boolean = false): Seq[Expression] = {
    val updateArg = getContext match {
      case ScAssignStmt(mc: ScMethodCall, rExpr) if includeUpdateCall && mc == this => rExpr
      case _ => None
    }

    val defaultArgs: Seq[ScExpression] = argumentExpressions ++ updateArg

    if (isNamedDynamic) {
      defaultArgs.map {
        expr =>
          val actualExpr = expr match {
            case a: ScAssignStmt =>
              a.getLExpression match {
                case ref: ScReferenceExpression if ref.qualifier.isEmpty => a.getRExpression.getOrElse(expr)
                case _ => expr
              }
            case _ => expr
          }
          new Expression(actualExpr) {
            override def getTypeAfterImplicitConversion(checkImplicits: Boolean, isShape: Boolean,
                                                        _expectedOption: Option[ScType]): (TypeResult, collection.Set[ImportUsed]) = {
              val expectedOption = _expectedOption.map {
                case TupleType(comps) if comps.length == 2 => comps(1)
                case t => t
              }
              val (res, imports) = super.getTypeAfterImplicitConversion(checkImplicits, isShape, expectedOption)
              val scope = MethodInvocationImpl.this.resolveScope

              val str = ScalaPsiManager.instance.getCachedClass(scope, "java.lang.String")
              val stringType = str.map(ScalaType.designator(_)).getOrElse(Any)
              (res.map(tp => TupleType(Seq(stringType, tp))), imports)
            }
          }
      }
    } else defaultArgs
  }

  private def invokedResolveResult: Option[ScalaResolveResult] = getEffectiveInvokedExpr match {
    case ref: ScReferenceExpression => ref.bind()
    case _ => None
  }

  private def findApplyUpdateOrDynamic(invokedType: ScType,
                                       useExpectedType: Boolean,
                                       isDynamic: Boolean): Option[SyntheticCase] = {
    def updateType(result: ScalaResolveResult) = {
      val polymorphicType = result.element.asInstanceOf[PsiMethod]
        .methodTypeProvider(elementScope)
        .polymorphicType(result.substitutor)
        .updateTypeOfDynamicCall(result.isDynamic)

      val updatedPolymorphicType = (invokedType, polymorphicType) match {
        case (ScTypePolymorphicType(_, head), ScTypePolymorphicType(internal, tail)) =>
          removeBadBounds(ScTypePolymorphicType(internal, head ++ tail))
        case (ScTypePolymorphicType(_, head), _) => ScTypePolymorphicType(polymorphicType, head)
        case _ => polymorphicType
      }

      updatedPolymorphicType.updateAccordingToExpectedType(useExpectedType, this)
    }

    processTypeForUpdateOrApplyCandidates(this, invokedType, isShape = false, isDynamic = isDynamic) match {
      case Array(result) if result.element.isInstanceOf[PsiMethod] =>
        val updatedProcessedType = updateType(result).updateAccordingToExpectedType(useExpectedType, this)

        val maybeFull = checkApplication(updatedProcessedType, useExpectedType, Some(result))
        Some(SyntheticCase(
          maybeFull.getOrElse(RegularCase(updatedProcessedType, Seq(new DoesNotTakeParameters))),
          result,
          maybeFull.map(_ => result)
        ))
      case _ => None
    }
  }
}

object MethodInvocationImpl {

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
                                   applyOrUpdate: Option[ScalaResolveResult]) extends InvocationData {
    override def typeResult: TypeResult = full.typeResult
  }

  private case class FailureCase(typeResult: Left[Failure, ScType]) extends InvocationData

}