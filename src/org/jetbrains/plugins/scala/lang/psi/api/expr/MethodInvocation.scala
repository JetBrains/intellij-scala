package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.types.result.{TypingContext, Success, TypeResult}
import org.jetbrains.plugins.scala.lang.psi.types._
import nonvalue.{TypeParameter, Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.{types, ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import com.intellij.psi.{PsiNamedElement, PsiElement}
import org.jetbrains.plugins.scala.extensions.toSeqExt
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.lang.languageLevel.ScalaLanguageLevel
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ResolvableReferenceExpression}
import scala.collection

/**
 * Pavel Fatin, Alexander Podkhalyuzin.
 */

// A common trait for Infix, Postfix and Prefix expressions
// and Method calls to handle them uniformly
trait MethodInvocation extends ScExpression with ScalaPsiElement {
  /**
   * For Infix, Postfix and Prefix expressions
   * it's refernce expression for operation
   * @return method reference or invoked expression for calls
   */
  def getInvokedExpr: ScExpression

  /**
   * @return call arguments
   */
  def argumentExpressions: Seq[ScExpression]

  /**
   * Unwraps parenthesised expression for method calls
   * @return unwrapped invoked expression
   */
  def getEffectiveInvokedExpr: ScExpression = getInvokedExpr

  /**
   * Important method for method calls like: foo(expr) = assign.
   * Usually this is same as argumentExpressions
   * @return arguments with additional argument if call in update position
   */
  def argumentExpressionsIncludeUpdateCall: Seq[ScExpression] = argumentExpressions

  /**
   * Seq of application problems like type mismatch.
   * @return seq of application problems
   */
  def applicationProblems: Seq[ApplicabilityProblem] = {
    getUpdatableUserData(MethodInvocation.APPLICABILITY_PROBLEMS_VAR_KEY)(Seq.empty)
  }

  /**
   * @return map of expressions and parameters
   */
  def matchedParameters: Seq[(ScExpression, Parameter)] = {
    var res = matchedParametersCache
    if (res == null) {
      res = matchedParametersInner.map(a => a.swap).filter(a => a._1 != null) //todo: catch when expression is null
      matchedParametersCache = res
    }
    res
  }

  @volatile
  private var matchedParametersCache: Seq[(ScExpression, Parameter)] = null

  /**
   * @return map of expressions and parameters
   */
  def matchedParametersMap: Map[Parameter, Seq[ScExpression]] = {
    matchedParametersInner.groupBy(_._1).map(t => t.copy(_2 = t._2.map(_._2)))
  }

  private def matchedParametersInner: Seq[(Parameter, ScExpression)] = {
    getUpdatableUserData(MethodInvocation.MATCHED_PARAMETERS_VAR_KEY)(Seq.empty)
  }

  /**
   * In case if invoked expression converted implicitly to invoke apply or update method
   * @return imports used for implicit conversion
   */
  def getImportsUsed: collection.Set[ImportUsed] = {
    getUpdatableUserData(MethodInvocation.IMPORTS_USED_KEY)(collection.Set.empty[ImportUsed])
  }

  /**
   * In case if invoked expression converted implicitly to invoke apply or update method
   * @return actual conversion element
   */
  def getImplicitFunction: Option[PsiNamedElement] = {
    getUpdatableUserData(MethodInvocation.IMPLICIT_FUNCTION_KEY)(None)
  }

  /**
   * true if this call is syntactic sugar for apply or update method.
   */
  def isApplyOrUpdateCall: Boolean = applyOrUpdateElement.isDefined

  def applyOrUpdateElement: Option[ScalaResolveResult] = {
    getUpdatableUserData(MethodInvocation.APPLY_OR_UPDATE_KEY)(None)
  }

  /**
   * It's arguments for method and infix call.
   * For prefix and postfix call it's just operation.
   * @return Element, which reflects arguments
   */
  def argsElement: PsiElement

  /**
   * This method useful in case if you want to update some polymorphic type
   * according to method call expected type
   */
  def updateAccordingToExpectedType(nonValueType: TypeResult[ScType],
                                    check: Boolean = false): TypeResult[ScType] = {
    InferUtil.updateAccordingToExpectedType(nonValueType, fromImplicitParameters = false, filterTypeParams = false,
      expectedType = expectedType(), expr = this, check = check)
  }

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
    var nonValueType: TypeResult[ScType] = getEffectiveInvokedExpr.getNonValueType(TypingContext.empty)
    this match {
      case _: ScPrefixExpr => return nonValueType //no arg exprs, just reference expression type
      case _: ScPostfixExpr => return nonValueType //no arg exprs, just reference expression type
      case _ =>
    }

    val withExpectedType = useExpectedType && expectedType() != None //optimization to avoid except

    if (useExpectedType) nonValueType = updateAccordingToExpectedType(nonValueType, check = true)

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
        localTypeInferenceWithApplicabilityExt(retType, parameters, t, typeParams, safeCheck = withExpectedType)
      }
    }

    def tuplizyCase(exprs: Seq[Expression])
                   (fun: (Seq[Expression]) => (ScType, scala.Seq[ApplicabilityProblem],
                           Seq[(Parameter, ScExpression)], Seq[(Parameter, ScType)])): ScType = {
      val c = fun(exprs)
      def tail: ScType = {
        setApplicabilityProblemsVar(c._2)
        setMatchedParametersVar(c._3)
        val dependentSubst = new ScSubstitutor(() => {
          val level = ScalaLanguageLevel.getLanguageLevel(this)
          if (level.isThoughScala2_10) {
            c._4.toMap
          } else Map.empty
        })
        dependentSubst.subst(c._1)
      }
      if (!c._2.isEmpty) {
        ScalaPsiUtil.tuplizy(exprs, getResolveScope, getManager, ScalaPsiUtil.firstLeaf(this)).map {e =>
          val cd = fun(e)
          if (!cd._2.isEmpty) tail
          else {
            setApplicabilityProblemsVar(cd._2)
            setMatchedParametersVar(cd._3)
            val dependentSubst = new ScSubstitutor(() => {
              val level = ScalaLanguageLevel.getLanguageLevel(this)
              if (level.isThoughScala2_10) {
                cd._4.toMap
              } else Map.empty
            })
            dependentSubst.subst(cd._1)
          }
        }.getOrElse(tail)
      } else tail
    }

    def functionParams(params: Seq[ScType]): Seq[Parameter] = {
      val functionName = "scala.Function" + params.length
      val functionClass = Option(ScalaPsiManager.instance(getProject).getCachedClass(functionName, getResolveScope,
        ScalaPsiManager.ClassCategory.TYPE)).flatMap {case t: ScTrait => Option(t) case _ => None}
      val applyFunction = functionClass.flatMap(_.functions.find(_.name == "apply"))
      params.mapWithIndex {
        case (tp, i) =>
          new Parameter("v" + (i + 1), None, tp, tp, false, false, false, i, applyFunction.map(_.parameters.apply(i)))
      }
    }

    def checkApplication(tpe: ScType, args: Seq[Expression]): Option[ScType] = tpe match {
      case ScFunctionType(retType: ScType, params: Seq[ScType]) =>
        Some(checkConformance(retType, args, functionParams(params)))
      case ScMethodType(retType, params, _) =>
        Some(checkConformance(retType, args, params))
      case ScTypePolymorphicType(ScMethodType(retType, params, _), typeParams) =>
        Some(checkConformanceWithInference(retType, args, typeParams, params))
      case ScTypePolymorphicType(ScFunctionType(retType, params), typeParams) =>
        Some(checkConformanceWithInference(retType, args, typeParams, functionParams(params)))
      case _ => None
    }

    val invokedType: ScType = nonValueType.getOrElse(return nonValueType)

    def args(includeUpdateCall: Boolean = false, isNamedDynamic: Boolean = false): Seq[Expression] = {
      def default: Seq[ScExpression] =
        if (includeUpdateCall) argumentExpressionsIncludeUpdateCall
        else  argumentExpressions
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
                                                          expectedOption: Option[ScType]): (TypeResult[ScType], collection.Set[ImportUsed]) = {
                val (res, imports) = super.getTypeAfterImplicitConversion(checkImplicits, isShape, expectedOption)
                val str = ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "java.lang.String")
                val stringType = if (str != null) ScType.designator(str) else types.Any
                (res.map(tp => ScTupleType(Seq(stringType, tp))(getProject, getResolveScope)), imports)
              }
            }
        }
      } else default
    }

    def isApplyDynamicNamed: Boolean = {
      getEffectiveInvokedExpr match {
        case ref: ScReferenceExpression =>
          ref.bind().exists(result => result.isDynamic && result.name == ResolvableReferenceExpression.APPLY_DYNAMIC_NAMED)
        case _ => false
      }
    }

    var res: ScType = checkApplication(invokedType, args(isNamedDynamic = isApplyDynamicNamed)).getOrElse {
      var (processedType, importsUsed, implicitFunction, applyOrUpdateResult) =
        ScalaPsiUtil.processTypeForUpdateOrApply(invokedType, this, isShape = false).getOrElse {
          (types.Nothing, Set.empty[ImportUsed], None, this.applyOrUpdateElement)
        }
      if (useExpectedType) {
        updateAccordingToExpectedType(Success(processedType, None)).foreach(x => processedType = x)
      }
      setApplyOrUpdate(applyOrUpdateResult)
      setImportsUsed(importsUsed)
      setImplicitFunction(implicitFunction)
      val isNamedDynamic: Boolean =
        applyOrUpdateResult.exists(result => result.isDynamic &&
          result.name == ResolvableReferenceExpression.APPLY_DYNAMIC_NAMED)
      checkApplication(processedType, args(includeUpdateCall = true, isNamedDynamic)).getOrElse {
        setApplyOrUpdate(None)
        setApplicabilityProblemsVar(Seq(new DoesNotTakeParameters))
        setMatchedParametersVar(Seq())
        processedType
      }
    }

    //Implicit parameters
    val checkImplicitParameters = withEtaExpansion(this)
    if (checkImplicitParameters) {
      val tuple = InferUtil.updateTypeWithImplicitParameters(res, this, None, useExpectedType)
      res = tuple._1
      implicitParameters = tuple._2
    }

    Success(res, Some(this))
  }

  def setApplicabilityProblemsVar(seq: Seq[ApplicabilityProblem]) {
    val modCount: Long = getManager.getModificationTracker.getModificationCount
    putUserData(MethodInvocation.APPLICABILITY_PROBLEMS_VAR_KEY, (modCount, seq))
  }

  private def setMatchedParametersVar(seq: Seq[(Parameter, ScExpression)]) {
    val modCount: Long = getManager.getModificationTracker.getModificationCount
    matchedParametersCache = null
    putUserData(MethodInvocation.MATCHED_PARAMETERS_VAR_KEY, (modCount, seq))
  }

  def setImportsUsed(set: collection.Set[ImportUsed]) {
    val modCount: Long = getManager.getModificationTracker.getModificationCount
    putUserData(MethodInvocation.IMPORTS_USED_KEY, (modCount, set))
  }

  def setImplicitFunction(opt: Option[PsiNamedElement]) {
    val modCount: Long = getManager.getModificationTracker.getModificationCount
    putUserData(MethodInvocation.IMPLICIT_FUNCTION_KEY, (modCount, opt))
  }

  def setApplyOrUpdate(opt: Option[ScalaResolveResult]) {
    val modCount: Long = getManager.getModificationTracker.getModificationCount
    putUserData(MethodInvocation.APPLY_OR_UPDATE_KEY, (modCount, opt))
  }

  private def getUpdatableUserData[Res](key: Key[(Long, Res)])(default: =>Res): Res = {
    val modCount = getManager.getModificationTracker.getModificationCount
    def getData = Option(getUserData(key)).getOrElse(-1L, default)
    getData match {
      case (`modCount`, res) => res
      case _ =>
        getType(TypingContext.empty) //update if needed
        getData match {
          case (`modCount`, res) => res
          case _ => default //todo: should we throw an exception in this case?
        }
    }
  }
}

object MethodInvocation {
  def unapply(invocation: MethodInvocation) =
    Some(invocation.getInvokedExpr, invocation.argumentExpressions)

  private val APPLICABILITY_PROBLEMS_VAR_KEY: Key[(Long, Seq[ApplicabilityProblem])] = Key.create("applicability.problems.var.key")
  private val MATCHED_PARAMETERS_VAR_KEY: Key[(Long, Seq[(Parameter, ScExpression)])] = Key.create("matched.parameter.var.key")
  private val IMPORTS_USED_KEY: Key[(Long, collection.Set[ImportUsed])] = Key.create("imports.used.method.invocation.key")
  private val IMPLICIT_FUNCTION_KEY: Key[(Long, Option[PsiNamedElement])] = Key.create("implicit.function.method.invocation.key")
  private val APPLY_OR_UPDATE_KEY: Key[(Long, Option[ScalaResolveResult])] = Key.create("apply.or.update.key")
}