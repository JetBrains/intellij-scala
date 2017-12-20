package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.processTypeForUpdateOrApplyCandidates
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ExpectedTypesImpl.TypeResultEx
import org.jetbrains.plugins.scala.lang.psi.impl.expr.MethodInvocationImpl._
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, FunctionType, Nothing, TupleType, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ApplicabilityProblem, Compatibility, DoesNotTakeParameters, ScSubstitutor, ScType, ScalaType}
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}
import org.jetbrains.plugins.scala.project.ProjectContext


/**
  * Nikolay.Tropin
  * 19-Dec-17
  */
abstract class MethodInvocationImpl(node: ASTNode) extends ScExpressionImplBase(node) with MethodInvocation {

  override protected def innerType: TypeResult = innerTypeExt.typeResult

  override def applicationProblems: Seq[ApplicabilityProblem] = innerTypeExt.problems

  override def getImportsUsed: collection.Set[ImportUsed] = innerTypeExt.importsUsed

  override def getImplicitFunction: Option[ScalaResolveResult] = innerTypeExt.implicitFunction

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

  private def tryToGetInnerTypeExt(useExpectedType: Boolean): InvocationData = {

    val nonValueType: TypeResult = getEffectiveInvokedExpr.getNonValueType()
    this match {
      case _: ScPrefixExpr => return InvocationData.Empty(nonValueType) //no arg exprs, just reference expression type
      case _: ScPostfixExpr => return InvocationData.Empty(nonValueType) //no arg exprs, just reference expression type
      case _ =>
    }

    val withExpectedType = useExpectedType && this.expectedType().isDefined //optimization to avoid except

    val updatedNonValueType =
      if (useExpectedType) nonValueType.updateAccordingToExpectedType(this, canThrowSCE = true)
      else nonValueType

    val invokedType: ScType = updatedNonValueType.getOrElse(return InvocationData.Empty(updatedNonValueType))

    val res: InvocationData.Success = checkApplication(invokedType, args(isNamedDynamic = resolvesToApplyDynamicNamed), withExpectedType) match {
      case Some(s) => s
      case None =>
        val updateApplyData =
          processTypeForUpdateOrApply(invokedType, this, isShape = false)
            .getOrElse(InvocationData.Success(Nothing))

        val processedType = updateApplyData.inferredType
        val updatedProcessedType =
          if (useExpectedType) Right(processedType).updateAccordingToExpectedType(this).getOrElse(processedType)
          else processedType

        val isNamedDynamic: Boolean =
          updateApplyData.applyOrUpdateElem.exists(DynamicResolveProcessor.isApplyDynamicNamed)

        checkApplication(updatedProcessedType, args(includeUpdateCall = true, isNamedDynamic), withExpectedType)
          .map(_.withApplyUpdate(updateApplyData))
          .getOrElse {
            InvocationData.Success(
              updatedProcessedType,
              Seq(new DoesNotTakeParameters),
              Seq.empty,
              Seq.empty,
              updateApplyData.importsUsed,
              updateApplyData.implicitFunction,
              None)
          }
    }

    val (newType, params) = this.updatedWithImplicitParameters(res.inferredType, useExpectedType)
    setImplicitParameters(params)

    res.copy(inferredType = newType)
  }

  private def tuplizyCase(exprs: Seq[Expression])
                                      (fun: (Seq[Expression]) => InvocationData.Success): InvocationData.Success = {

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

  private def checkConformance(retType: ScType, psiExprs: Seq[Expression], parameters: Seq[Parameter]): InvocationData.Success = {
    tuplizyCase(psiExprs) { t =>
      val result = Compatibility.checkConformanceExt(checkNames = true, parameters = parameters, exprs = t,
        checkWithImplicits = true, isShapesResolve = false)
      InvocationData.Success(retType, result.problems, result.matchedArgs, result.matchedTypes)
    }
  }

  private def checkConformanceWithInference(retType: ScType,
                                            withExpectedType: Boolean,
                                            psiExprs: Seq[Expression],
                                            typeParams: Seq[TypeParameter],
                                            parameters: Seq[Parameter]): InvocationData.Success = {
    tuplizyCase(psiExprs) { t =>
      val (inferredType, problems, matchedParams, matchedTypes) =
        InferUtil.localTypeInferenceWithApplicabilityExt(retType, parameters, t, typeParams, canThrowSCE = withExpectedType)
      InvocationData.Success(inferredType, problems, matchedParams, matchedTypes)
    }
  }

  private def checkApplication(tpe: ScType, args: Seq[Expression], withExpectedType: Boolean): Option[InvocationData.Success] = tpe match {
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

  private def args(includeUpdateCall: Boolean = false, isNamedDynamic: Boolean = false): Seq[Expression] = {
    def default: Seq[ScExpression] =
      if (includeUpdateCall) argumentExpressionsIncludeUpdateCall
      else argumentExpressions

    if (isNamedDynamic) {
      default.map {
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
              implicit val project = getProject
              implicit val scope = MethodInvocationImpl.this.resolveScope

              val str = ScalaPsiManager.instance(project).getCachedClass(scope, "java.lang.String")
              val stringType = str.map(ScalaType.designator(_)).getOrElse(Any)
              (res.map(tp => TupleType(Seq(stringType, tp))), imports)
            }
          }
      }
    } else default
  }

  private def resolvesToApplyDynamicNamed: Boolean = {
    getEffectiveInvokedExpr match {
      case ref: ScReferenceExpression =>
        ref.bind().exists(DynamicResolveProcessor.isApplyDynamicNamed)
      case _ => false
    }
  }
}

object MethodInvocationImpl {
  
  private def processTypeForUpdateOrApply(tp: ScType, call: MethodInvocation, isShape: Boolean): Option[InvocationData.Success] = {
    implicit val ctx: ProjectContext = call

    def checkCandidates(withDynamic: Boolean = false): Option[InvocationData.Success] = {
      val candidates: Array[ScalaResolveResult] = processTypeForUpdateOrApplyCandidates(call, tp, isShape, isDynamic = withDynamic)
      PartialFunction.condOpt(candidates) {
        case Array(r@ScalaResolveResult(fun: PsiMethod, s: ScSubstitutor)) =>
          def update(tp: ScType): ScType = {
            if (r.isDynamic) DynamicResolveProcessor.getDynamicReturn(tp)
            else tp
          }

          val res = fun match {
            case fun: ScFun =>
              val scType = update(s.subst(fun.polymorphicType))
              InvocationData.Success.fromApplyUpdate(scType, Some(r))
            case fun: ScFunction =>
              val scType = update(s.subst(fun.polymorphicType()))
              InvocationData.Success.fromApplyUpdate(scType, Some(r))
            case meth: PsiMethod =>
              val scType = update(ResolveUtils.javaPolymorphicType(meth, s, call.resolveScope))
              InvocationData.Success.fromApplyUpdate(scType, Some(r))
          }
          call.getInvokedExpr.getNonValueType() match {
            case Right(ScTypePolymorphicType(_, typeParams)) =>
              val fixedType = res.inferredType match {
                case ScTypePolymorphicType(internal, typeParams2) =>
                  ScalaPsiUtil.removeBadBounds(ScTypePolymorphicType(internal, typeParams ++ typeParams2))
                case _ => ScTypePolymorphicType(res.inferredType, typeParams)
              }
              res.copy(inferredType = fixedType)
            case _ => res
          }
      }
    }

    checkCandidates().orElse(checkCandidates(withDynamic = true))
  }

}