package org.jetbrains.plugins.scala
package lang.psi.api.expr

import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolvableReferenceExpression, ScalaResolveResult}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_10
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
  def applicationProblems: Seq[ApplicabilityProblem] = {
    getType()
    problemsVar
  }

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

  private def matchedParametersInner: Seq[(Parameter, ScExpression)] = {
    getType()
    matchedParamsVar
  }

  /**
    * In case if invoked expression converted implicitly to invoke apply or update method
    *
    * @return imports used for implicit conversion
    */
  def getImportsUsed: collection.Set[ImportUsed] = {
    getType()
    importsUsedVar
  }

  /**
    * In case if invoked expression converted implicitly to invoke apply or update method
    *
    * @return actual conversion element
    */
  def getImplicitFunction: Option[PsiNamedElement] = {
    getType()
    implicitFunctionVar
  }

  /**
    * true if this call is syntactic sugar for apply or update method.
    */
  def isApplyOrUpdateCall: Boolean = applyOrUpdateElement.isDefined

  def applyOrUpdateElement: Option[ScalaResolveResult] = {
    getType()
    applyOrUpdateElemVar
  }

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

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    try {
      tryToGetInnerType(ctx, useExpectedType = true)
    } catch {
      case _: SafeCheckException =>
        tryToGetInnerType(ctx, useExpectedType = false)
    }
  }

  private def tryToGetInnerType(ctx: TypingContext, useExpectedType: Boolean): TypeResult[ScType] = {
    var problemsLocal: Seq[ApplicabilityProblem] = Seq.empty
    var matchedParamsLocal: Seq[(Parameter, ScExpression)] = Seq.empty
    var importsUsedLocal: collection.Set[ImportUsed] = collection.Set.empty
    var implicitFunctionLocal: Option[PsiNamedElement] = None
    var applyOrUpdateElemLocal: Option[ScalaResolveResult] = None

    def updateCacheFields(): Unit = {
      problemsVar = problemsLocal
      matchedParamsVar = matchedParamsLocal
      importsUsedVar = importsUsedLocal
      implicitFunctionVar = implicitFunctionLocal
      applyOrUpdateElemVar = applyOrUpdateElemLocal
    }

    var nonValueType: TypeResult[ScType] = getEffectiveInvokedExpr.getNonValueType(TypingContext.empty)
    this match {
      case _: ScPrefixExpr => return nonValueType //no arg exprs, just reference expression type
      case _: ScPostfixExpr => return nonValueType //no arg exprs, just reference expression type
      case _ =>
    }

    val withExpectedType = useExpectedType && this.expectedType().isDefined //optimization to avoid except

    if (useExpectedType) nonValueType = this.updateAccordingToExpectedType(nonValueType, check = true)

    def checkConformance(retType: ScType, psiExprs: Seq[Expression], parameters: Seq[Parameter]) = {
      tuplizyCase(psiExprs) { t =>
        val result = Compatibility.checkConformanceExt(checkNames = true, parameters = parameters, exprs = t,
          checkWithImplicits = true, isShapesResolve = false)
        (retType, result.problems, result.matchedArgs, result.matchedTypes)
      }
    }

    def checkConformanceWithInference(retType: ScType, psiExprs: Seq[Expression],
                                      typeParams: Seq[TypeParameter], parameters: Seq[Parameter]) = {
      tuplizyCase(psiExprs) { t =>
        InferUtil.localTypeInferenceWithApplicabilityExt(retType, parameters, t, typeParams, safeCheck = withExpectedType)
      }
    }

    def tuplizyCase(exprs: Seq[Expression])
                   (fun: (Seq[Expression]) => (ScType, scala.Seq[ApplicabilityProblem],
                     Seq[(Parameter, ScExpression)], Seq[(Parameter, ScType)])): ScType = {
      val c = fun(exprs)

      def tail: ScType = {
        problemsLocal = c._2
        matchedParamsLocal = c._3
        val dependentSubst = ScSubstitutor(() => {
          this.scalaLanguageLevel match {
            case Some(level) if level < Scala_2_10 => Map.empty
            case _ => c._4.toMap
          }
        })
        dependentSubst.subst(c._1)
      }

      if (c._2.nonEmpty) {
        ScalaPsiUtil.tuplizy(exprs, getResolveScope, getManager, ScalaPsiUtil.firstLeaf(this)).map { e =>
          val cd = fun(e)
          if (cd._2.nonEmpty) tail
          else {
            problemsLocal = cd._2
            matchedParamsLocal = cd._3
            val dependentSubst = ScSubstitutor(() => {
              this.scalaLanguageLevel match {
                case Some(level) if level < Scala_2_10 => Map.empty
                case _ => cd._4.toMap
              }
            })
            dependentSubst.subst(cd._1)
          }
        }.getOrElse(tail)
      } else tail
    }

    def functionParams(params: Seq[ScType]): Seq[Parameter] = {
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

    def checkApplication(tpe: ScType, args: Seq[Expression]): Option[ScType] = tpe match {
      case ScMethodType(retType, params, _) =>
        Some(checkConformance(retType, args, params))
      case ScTypePolymorphicType(ScMethodType(retType, params, _), typeParams) =>
        Some(checkConformanceWithInference(retType, args, typeParams, params))
      case ScTypePolymorphicType(FunctionType(retType, params), typeParams) =>
        Some(checkConformanceWithInference(retType, args, typeParams, functionParams(params)))
      case any if ScalaPsiUtil.isSAMEnabled(this) =>
        ScalaPsiUtil.toSAMType(any, this) match {
          case Some(FunctionType(retType: ScType, params: Seq[ScType])) =>
            Some(checkConformance(retType, args, functionParams(params)))
          case _ => None
        }
      case _ => None
    }

    val invokedType: ScType = nonValueType.getOrElse(return nonValueType)

    def args(includeUpdateCall: Boolean = false, isNamedDynamic: Boolean = false): Seq[Expression] = {
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
                                                          _expectedOption: Option[ScType]): (TypeResult[ScType], collection.Set[ImportUsed]) = {
                val expectedOption = _expectedOption.map {
                  case TupleType(comps) if comps.length == 2 => comps(1)
                  case t => t
                }
                val (res, imports) = super.getTypeAfterImplicitConversion(checkImplicits, isShape, expectedOption)
                implicit val project = getProject
                implicit val scope = getResolveScope

                val str = ScalaPsiManager.instance(project).getCachedClass(scope, "java.lang.String")
                val stringType = str.map(ScalaType.designator(_)).getOrElse(Any)
                (res.map(tp => TupleType(Seq(stringType, tp))), imports)
              }
            }
        }
      } else default
    }

    def isApplyDynamicNamed: Boolean = {
      getEffectiveInvokedExpr match {
        case ref: ScReferenceExpression =>
          ref.bind().exists(result => result.isDynamic && result.name == DynamicResolveProcessor.APPLY_DYNAMIC_NAMED)
        case _ => false
      }
    }

    var res: ScType = checkApplication(invokedType, args(isNamedDynamic = isApplyDynamicNamed)) match {
      case Some(s) => s
      case None =>
        var (processedType, importsUsed, implicitFunction, applyOrUpdateResult) =
          ScalaPsiUtil.processTypeForUpdateOrApply(invokedType, this, isShape = false).getOrElse {
            (Nothing, Set.empty[ImportUsed], None, this.applyOrUpdateElement)
          }
        if (useExpectedType) {
          this.updateAccordingToExpectedType(Success(processedType, None)).foreach(x => processedType = x)
        }
        applyOrUpdateElemLocal = applyOrUpdateResult
        importsUsedLocal = importsUsed
        implicitFunctionLocal = implicitFunction
        val isNamedDynamic: Boolean =
          applyOrUpdateResult.exists(result => result.isDynamic &&
            result.name == DynamicResolveProcessor.APPLY_DYNAMIC_NAMED)
        checkApplication(processedType, args(includeUpdateCall = true, isNamedDynamic)).getOrElse {
          applyOrUpdateElemLocal = None
          problemsLocal = Seq(new DoesNotTakeParameters)
          matchedParamsLocal = Seq()
          processedType
        }
    }

    //Implicit parameters
    val checkImplicitParameters = withEtaExpansion(this)
    if (checkImplicitParameters) {
      val tuple = InferUtil.updateTypeWithImplicitParameters(res, this, None, useExpectedType, fullInfo = false)
      res = tuple._1
      implicitParameters = tuple._2
    }

    updateCacheFields()

    Success(res, Some(this))
  }

  @volatile private var problemsVar: Seq[ApplicabilityProblem] = Seq.empty
  @volatile private var matchedParamsVar: Seq[(Parameter, ScExpression)] = Seq.empty
  @volatile private var importsUsedVar: collection.Set[ImportUsed] = collection.Set.empty
  @volatile private var implicitFunctionVar: Option[PsiNamedElement] = None
  @volatile private var applyOrUpdateElemVar: Option[ScalaResolveResult] = None

  //used in Play
  def setApplicabilityProblemsVar(seq: Seq[ApplicabilityProblem]): Unit = {
    problemsVar = seq
  }

}

object MethodInvocation {
  def unapply(invocation: MethodInvocation) = Some(invocation.getInvokedExpr, invocation.argumentExpressions)

  implicit class MethodInvocationExt(val invocation: MethodInvocation) extends AnyVal {
    private implicit def elementScope = invocation.elementScope

    private implicit def typeSystem = invocation.typeSystem

    /**
      * This method useful in case if you want to update some polymorphic type
      * according to method call expected type
      */
    def updateAccordingToExpectedType(nonValueType: TypeResult[ScType],
                                      check: Boolean = false): TypeResult[ScType] = {
      InferUtil.updateAccordingToExpectedType(nonValueType, fromImplicitParameters = false, filterTypeParams = false,
        expectedType = invocation.expectedType(), expr = invocation, check = check)
    }

  }

}
