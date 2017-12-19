package org.jetbrains.plugins.scala
package lang.psi.api.expr

import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.expr.MethodInvocation.{InvocationData, InvocationDataEmpty, InvocationDataSuccess, UpdateApplyData}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}
import org.jetbrains.plugins.scala.project._

/**
  * Pavel Fatin, Alexander Podkhalyuzin.
  */

// A common trait for Infix, Postfix and Prefix expressions
// and Method calls to handle them uniformly
trait MethodInvocation extends ScExpression with ScalaPsiElement {
  /**
    * For Infix, Postfix and Prefix expressions
    * it's refernce expression for operation
    *
    * @return method reference or invoked expression for calls
    */
  def getInvokedExpr: ScExpression

  /**
    * @return call arguments
    */
  def argumentExpressions: Seq[ScExpression]

  /**
    * Unwraps parenthesised expression for method calls
    *
    * @return unwrapped invoked expression
    */
  def getEffectiveInvokedExpr: ScExpression = getInvokedExpr

  /**
    * Important method for method calls like: foo(expr) = assign.
    * Usually this is same as argumentExpressions
    *
    * @return arguments with additional argument if call in update position
    */
  def argumentExpressionsIncludeUpdateCall: Seq[ScExpression] = argumentExpressions

  /**
    * Seq of application problems like type mismatch.
    *
    * @return seq of application problems
    */
  def applicationProblems: Seq[ApplicabilityProblem] = innerTypeExt.problems

  /**
    * @return map of expressions and parameters
    */
  def matchedParameters: Seq[(ScExpression, Parameter)] = {
    matchedParametersInner.map(a => a.swap).filter(a => a._1 != null) //todo: catch when expression is null
  }

  /**
    * @return map of expressions and parameters
    */
  def matchedParametersMap: Map[Parameter, Seq[ScExpression]] = {
    matchedParametersInner.groupBy(_._1).map(t => t.copy(_2 = t._2.map(_._2)))
  }

  private def matchedParametersInner: Seq[(Parameter, ScExpression)] = innerTypeExt.matchedParams

  /**
    * In case if invoked expression converted implicitly to invoke apply or update method
    *
    * @return imports used for implicit conversion
    */
  def getImportsUsed: collection.Set[ImportUsed] = innerTypeExt.importsUsed

  /**
    * In case if invoked expression converted implicitly to invoke apply or update method
    *
    * @return actual conversion element
    */
  def getImplicitFunction: Option[ScalaResolveResult] = innerTypeExt.implicitFunction

  /**
    * true if this call is syntactic sugar for apply or update method.
    */
  def isApplyOrUpdateCall: Boolean = applyOrUpdateElement.isDefined

  def applyOrUpdateElement: Option[ScalaResolveResult] = innerTypeExt.applyOrUpdateElem

  /**
    * It's arguments for method and infix call.
    * For prefix and postfix call it's just operation.
    *
    * @return Element, which reflects arguments
    */
  def argsElement: PsiElement

  /**
    * @return Is this method invocation in 'update' syntax sugar position.
    */
  def isUpdateCall: Boolean = false

  protected override def innerType: TypeResult = innerTypeExt.typeResult

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
      case _: ScPrefixExpr => return InvocationDataEmpty(nonValueType) //no arg exprs, just reference expression type
      case _: ScPostfixExpr => return InvocationDataEmpty(nonValueType) //no arg exprs, just reference expression type
      case _ =>
    }

    val withExpectedType = useExpectedType && this.expectedType().isDefined //optimization to avoid except

    val updatedNonValueType =
      if (useExpectedType) this.updateAccordingToExpectedType(nonValueType, canThrowSCE = true)
      else nonValueType

    val invokedType: ScType = updatedNonValueType.getOrElse(return InvocationDataEmpty(updatedNonValueType))

    val res: InvocationDataSuccess = checkApplication(invokedType, args(isNamedDynamic = isApplyDynamicNamed), withExpectedType) match {
      case Some(s) => s
      case None =>
        val updateApplyData =
          MethodInvocation.processTypeForUpdateOrApply(invokedType, this, isShape = false).getOrElse {
            UpdateApplyData(Nothing, Set.empty[ImportUsed], None, this.applyOrUpdateElement)
          }

        val processedType = updateApplyData.processedType
        val updatedProcessedType =
          if (useExpectedType) this.updateAccordingToExpectedType(Right(processedType)).toOption.getOrElse(processedType)
          else processedType

        val isNamedDynamic: Boolean =
          updateApplyData.applyOrUpdateResult.exists(result => result.isDynamic &&
            result.name == DynamicResolveProcessor.APPLY_DYNAMIC_NAMED)

        checkApplication(updatedProcessedType, args(includeUpdateCall = true, isNamedDynamic), withExpectedType)
          .map(_.merge(updateApplyData))
          .getOrElse {
            InvocationDataSuccess(
              updatedProcessedType,
              Seq(new DoesNotTakeParameters),
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
                 (fun: (Seq[Expression]) => (ScType, scala.Seq[ApplicabilityProblem],
                   Seq[(Parameter, ScExpression)], Seq[(Parameter, ScType)])): InvocationDataSuccess = {
    val c = fun(exprs)

    def tail: InvocationDataSuccess = {
      val dependentSubst = ScSubstitutor(() => c._4.toMap)
      val scType = dependentSubst.subst(c._1)
      InvocationDataSuccess(scType, c._2, c._3, Set.empty, None, None)
    }

    if (c._2.nonEmpty) {
      ScalaPsiUtil.tuplizy(exprs, this.resolveScope, getManager, ScalaPsiUtil.firstLeaf(this)).map { e =>
        val cd = fun(e)
        if (cd._2.nonEmpty) tail
        else {
          val dependentSubst = ScSubstitutor(() => cd._4.toMap)
          val scType = dependentSubst.subst(cd._1)
          InvocationDataSuccess(scType, cd._2, cd._3, Set.empty, None, None)
        }
      }.getOrElse(tail)
    } else tail
  }

  private def checkConformance(retType: ScType, psiExprs: Seq[Expression], parameters: Seq[Parameter]): InvocationDataSuccess = {
    tuplizyCase(psiExprs) { t =>
      val result = Compatibility.checkConformanceExt(checkNames = true, parameters = parameters, exprs = t,
        checkWithImplicits = true, isShapesResolve = false)
      (retType, result.problems, result.matchedArgs, result.matchedTypes)
    }
  }

  private def checkConformanceWithInference(retType: ScType,
                                            withExpectedType: Boolean,
                                            psiExprs: Seq[Expression],
                                            typeParams: Seq[TypeParameter],
                                            parameters: Seq[Parameter]): InvocationDataSuccess = {
    tuplizyCase(psiExprs) { t =>
      InferUtil.localTypeInferenceWithApplicabilityExt(retType, parameters, t, typeParams, canThrowSCE = withExpectedType)
    }
  }

  private def checkApplication(tpe: ScType, args: Seq[Expression], withExpectedType: Boolean): Option[InvocationDataSuccess] = tpe match {
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
              implicit val scope = MethodInvocation.this.resolveScope

              val str = ScalaPsiManager.instance(project).getCachedClass(scope, "java.lang.String")
              val stringType = str.map(ScalaType.designator(_)).getOrElse(Any)
              (res.map(tp => TupleType(Seq(stringType, tp))), imports)
            }
          }
      }
    } else default
  }

  private def isApplyDynamicNamed: Boolean = {
    getEffectiveInvokedExpr match {
      case ref: ScReferenceExpression =>
        ref.bind().exists(result => result.isDynamic && result.name == DynamicResolveProcessor.APPLY_DYNAMIC_NAMED)
      case _ => false
    }
  }
}

object MethodInvocation {

  def unapply(methodInvocation: MethodInvocation): Option[(ScExpression, Seq[ScExpression])] =
    for {
      invocation <- Option(methodInvocation)
      expression = invocation.getInvokedExpr
      if expression != null
    } yield (expression, invocation.argumentExpressions)

  implicit class MethodInvocationExt(val invocation: MethodInvocation) extends AnyVal {
    private implicit def elementScope = invocation.elementScope

    /**
      * This method useful in case if you want to update some polymorphic type
      * according to method call expected type
      */
    def updateAccordingToExpectedType(nonValueType: TypeResult,
                                      canThrowSCE: Boolean = false): TypeResult = {
      InferUtil.updateAccordingToExpectedType(nonValueType, fromImplicitParameters = false, filterTypeParams = false,
        expectedType = invocation.expectedType(), expr = invocation, canThrowSCE)
    }

  }

  private sealed trait InvocationData {
    def typeResult: TypeResult
    def problems: Seq[ApplicabilityProblem]
    def matchedParams: Seq[(Parameter, ScExpression)]
    def importsUsed: collection.Set[ImportUsed]
    def implicitFunction: Option[ScalaResolveResult]
    def applyOrUpdateElem: Option[ScalaResolveResult]
  }

  private case class InvocationDataSuccess(inferredType: ScType,
                                           problems: Seq[ApplicabilityProblem] = Seq.empty,
                                           matchedParams: Seq[(Parameter, ScExpression)] = Seq.empty,
                                           importsUsed: collection.Set[ImportUsed] = Set.empty,
                                           implicitFunction: Option[ScalaResolveResult] = None,
                                           applyOrUpdateElem: Option[ScalaResolveResult] = None) extends InvocationData {
    def merge(updateApplyData: UpdateApplyData): InvocationDataSuccess = copy(
      importsUsed = updateApplyData.importsUsed,
      implicitFunction = updateApplyData.implicitFunction,
      applyOrUpdateElem = updateApplyData.applyOrUpdateResult
    )

    override def typeResult: TypeResult = Right(inferredType)
  }

  private case class InvocationDataEmpty(typeResult: TypeResult) extends InvocationData {
    def problems: Seq[ApplicabilityProblem] = Seq.empty
    def matchedParams: Seq[(Parameter, ScExpression)] = Seq.empty
    def importsUsed: collection.Set[ImportUsed] = Set.empty
    def implicitFunction: Option[ScalaResolveResult] = None
    def applyOrUpdateElem: Option[ScalaResolveResult] = None
  }

  private case class UpdateApplyData(processedType: ScType,
                                importsUsed: collection.Set[ImportUsed],
                                implicitFunction: Option[ScalaResolveResult],
                                applyOrUpdateResult: Option[ScalaResolveResult])

  private def processTypeForUpdateOrApply(tp: ScType, call: MethodInvocation, isShape: Boolean): Option[UpdateApplyData] = {
    implicit val ctx: ProjectContext = call

    def checkCandidates(withDynamic: Boolean = false): Option[UpdateApplyData] = {
      val candidates: Array[ScalaResolveResult] = processTypeForUpdateOrApplyCandidates(call, tp, isShape, isDynamic = withDynamic)
      PartialFunction.condOpt(candidates) {
        case Array(r@ScalaResolveResult(fun: PsiMethod, s: ScSubstitutor)) =>
          def update(tp: ScType): ScType = {
            if (r.isDynamic) DynamicResolveProcessor.getDynamicReturn(tp)
            else tp
          }

          val res = fun match {
            case fun: ScFun => UpdateApplyData(update(s.subst(fun.polymorphicType)), r.importsUsed, r.implicitConversion, Some(r))
            case fun: ScFunction => UpdateApplyData(update(s.subst(fun.polymorphicType())), r.importsUsed, r.implicitConversion, Some(r))
            case meth: PsiMethod => UpdateApplyData(update(ResolveUtils.javaPolymorphicType(meth, s, call.resolveScope)),
              r.importsUsed, r.implicitConversion, Some(r))
          }
          call.getInvokedExpr.getNonValueType() match {
            case Right(ScTypePolymorphicType(_, typeParams)) =>
              val fixedType = res.processedType match {
                case ScTypePolymorphicType(internal, typeParams2) =>
                  ScalaPsiUtil.removeBadBounds(ScTypePolymorphicType(internal, typeParams ++ typeParams2))
                case _ => ScTypePolymorphicType(res.processedType, typeParams)
              }
              res.copy(processedType = fixedType)
            case _ => res
          }
      }
    }

    checkCandidates().orElse(checkCandidates(withDynamic = true))
  }


}
