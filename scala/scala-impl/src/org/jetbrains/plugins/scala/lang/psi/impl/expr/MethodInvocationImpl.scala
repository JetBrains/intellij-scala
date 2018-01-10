package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroInvocationContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.processTypeForUpdateOrApplyCandidates
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ExpectedTypesImpl._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.MethodInvocationImpl._
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, FunctionType, TupleType, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import org.jetbrains.plugins.scala.lang.psi.types.{ApplicabilityProblem, Compatibility, ScSubstitutor, ScType, ScalaType}
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor._
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}


/**
  * Nikolay.Tropin
  * 19-Dec-17
  */
abstract class MethodInvocationImpl(node: ASTNode) extends ScExpressionImplBase(node) with MethodInvocation {

  override protected def innerType: TypeResult = innerTypeExt.typeResult

  override def applicationProblems: Seq[ApplicabilityProblem] = innerTypeExt.problems

  override def getImportsUsed: collection.Set[ImportUsed] = innerTypeExt.importsUsed

  override def getImplicitFunction: Option[ScalaResolveResult] = innerTypeExt.implicitConversion

  override def applyOrUpdateElement: Option[ScalaResolveResult] = innerTypeExt.applyOrUpdateElem

  override protected def matchedParametersInner: Seq[(Parameter, ScExpression)] = innerTypeExt.matchedParams

  @Cached(ModCount.getBlockModificationCount, this)
  private def innerTypeExt: InvocationData = {
    try {
      tryToGetInnerTypeExt(useExpectedType = true)
    } catch {
      case _: SafeCheckException =>
        tryToGetInnerTypeExt(useExpectedType = false)
    }
  }

  //this method works for ScInfixExpression and ScMethodCall
  private def tryToGetInnerTypeExt(useExpectedType: Boolean): InvocationData = {

    val nonValueType = getEffectiveInvokedExpr.getNonValueType() match {
      case Right(nv) =>
        nv.updateAccordingToExpectedType(useExpectedType, this, canThrowSCE = true)
      case failure =>
        return InvocationData.Plain(failure)
    }

    def simpleApplication = checkApplication(nonValueType, useExpectedType, invokedResolveResult)

    def applyUpdateOrDynamic = checkApplyUpdateOrDynamic(nonValueType, useExpectedType)

    val data = simpleApplication.orElse(applyUpdateOrDynamic)
    data match {
      case Some(res) =>
        val (newType, params) = this.updatedWithImplicitParameters(res.inferredType, useExpectedType)
        setImplicitParameters(params)
        res.copy(inferredType = newType)
      case None =>
        InvocationData.Plain(Failure("Suitable method not found"))
    }
  }

  private def tuplizyCase(exprs: Seq[Expression])
                         (fun: (Seq[Expression]) => InvocationData.Full): InvocationData.Full = {

    val nonTupled = fun(exprs)

    def tupledWithSubstitutedType =
      ScalaPsiUtil.tupled(exprs, this).flatMap { e =>
        val tupled = fun(e)
        tupled.withSubstitutedType
      }

    nonTupled.withSubstitutedType
      .orElse(tupledWithSubstitutedType)
      .getOrElse(nonTupled)
  }

  private def checkConformance(retType: ScType, psiExprs: Seq[Expression], parameters: Seq[Parameter]): InvocationData.Full = {
    tuplizyCase(psiExprs) { t =>
      val result = Compatibility.checkConformanceExt(checkNames = true, parameters = parameters, exprs = t,
        checkWithImplicits = true, isShapesResolve = false)
      InvocationData.Full(retType, result.problems, result.matchedArgs, result.matchedTypes)
    }
  }

  private def checkConformanceWithInference(retType: ScType,
                                            withExpectedType: Boolean,
                                            psiExprs: Seq[Expression],
                                            typeParams: Seq[TypeParameter],
                                            parameters: Seq[Parameter]): InvocationData.Full = {
    tuplizyCase(psiExprs) { t =>
      val (inferredType, problems, matchedParams, matchedTypes) =
        InferUtil.localTypeInferenceWithApplicabilityExt(retType, parameters, t, typeParams, canThrowSCE = withExpectedType)
      InvocationData.Full(inferredType, problems, matchedParams, matchedTypes)
    }
  }

  private def checkApplication(invokedNonValueType: ScType,
                               useExpectedType: Boolean,
                               resolveResult: Option[ScalaResolveResult]): Option[InvocationData.Full] = {

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
      case any if ScalaPsiUtil.isSAMEnabled(this) =>
        ScalaPsiUtil.toSAMType(any, this) match {
          case Some(FunctionType(retType: ScType, params: Seq[ScType])) =>
            Some(checkConformance(retType, args, functionParams(params)))
          case _ => None
        }
      case _ => None
    }
  }

  private def checkMacroExpansion(r: ScalaResolveResult): Option[InvocationData.Full] = {
    val macroEvaluator = ScalaMacroEvaluator.getInstance(getProject)
    val expansion = macroEvaluator.expandMacro(r.element, MacroInvocationContext(this, r))
    expansion
      .flatMap(_.getNonValueType().toOption)
      .map(InvocationData.Full(_))
  }

  private def checkApplyUpdateOrDynamic(invokedType: ScType, useExpectedType: Boolean): Option[InvocationData.Full] = {
    val applyUpdateRes =
      findApplyUpdateOrDynamic(invokedType, withDynamic = false).orElse {
        findApplyUpdateOrDynamic(invokedType, withDynamic = true)
    }

    applyUpdateRes match {
      case Some(ApplyUpdateResult(processedType, result)) =>

        val updatedProcessedType = processedType.updateAccordingToExpectedType(useExpectedType, this)

        checkApplication(updatedProcessedType, useExpectedType, Some(result))
          .map(_.withApplyUpdate(result))
          .orElse(InvocationData.noSuitableParameters(updatedProcessedType, result).toOption)
      case _ =>
        None
    }

  }

  private def functionParams(params: Seq[ScType]): Seq[Parameter] = {
    val functionName = s"scala.Function${params.length}"
    val functionClass = elementScope.getCachedClass(functionName)
      .collect {
        case t: ScTrait => t
      }
    val applyFunction = functionClass.flatMap(_.functions.find(_.name == "apply"))
    params.mapWithIndex {
      case (tp, i) =>
        new Parameter("v" + (i + 1), None, tp, tp, false, false, false, i, applyFunction.map(_.parameters.apply(i)))
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

  private def findApplyUpdateOrDynamic(invokedType: ScType, withDynamic: Boolean): Option[ApplyUpdateResult] = {
    val candidates = processTypeForUpdateOrApplyCandidates(this, invokedType, isShape = false, isDynamic = withDynamic)
    PartialFunction.condOpt(candidates) {
      case Array(r@ScalaResolveResult(fun: PsiMethod, s: ScSubstitutor)) =>

        val polyType = fun.polymorphicType(s).updateTypeOfDynamicCall(r.isDynamic)

        val fixedType = invokedType match {
          case ScTypePolymorphicType(_, typeParams) =>
            polyType match {
              case ScTypePolymorphicType(internal, typeParams2) =>
                ScalaPsiUtil.removeBadBounds(ScTypePolymorphicType(internal, typeParams ++ typeParams2))
              case _ => ScTypePolymorphicType(polyType, typeParams)
            }
          case _ => polyType
        }
        ApplyUpdateResult(fixedType, r)
    }
  }
}

object MethodInvocationImpl {
  private case class ApplyUpdateResult(invokedType: ScType, result: ScalaResolveResult)
}