package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypingContext, Success, TypeResult}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import com.intellij.psi.PsiElement

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
   * Usually this is same as argumentExpreesions
   * @return arguments with additional argument if call in update position
   */
  def argumentExpressionsIncludeUpdateCall: Seq[ScExpression] = argumentExpressions

  /**
   * Seq of application problems like type mismatch.
   * @return seq of application problems
   */
  def applicationProblems: Seq[ApplicabilityProblem] = {
    getType(TypingContext.empty) //update applicabilityProblemsVar if needed
    applicabilityProblemsVar
  }

  /**
   * @return map of expressions and parameters
   */
  def matchedParameters: Map[ScExpression, Parameter] = {
    getType(TypingContext.empty) //update matchedArgumentsVar if needed
    matchedParametersVar.map{ case (a, b) => (b, a)}.toMap
  }

  /**
   * It's arguments for method and infix call.
   * For prefix and postfix call it's just operation.
   * @return Element, which reflects arguments
   */
  def argsElement: PsiElement

  //todo: duplicate? see: ScExpression.*Type
  def updateAccordingToExpectedType(_nonValueType: TypeResult[ScType]): TypeResult[ScType] = {
    var nonValueType: TypeResult[ScType] = _nonValueType
    val fromUnderscoreSection: Boolean = getText.indexOf("_") match {
      case -1 => false
      case _ => {
        val unders = ScUnderScoreSectionUtil.underscores(this)
        if (unders.length == 0) false
        else true
      }
    }
    nonValueType match {
      case Success(ScTypePolymorphicType(m@ScMethodType(internal, params, impl), typeParams), _) if expectedType != None => {
        def updateRes(expected: ScType) {
          val subIntenal: ScType = internal match {
            case ScMethodType(internal, _, impl) if impl => internal
            case _ => internal
          }
          val update: ScTypePolymorphicType = ScalaPsiUtil.localTypeInference(subIntenal,
            Seq(Parameter("", expected, false, false, false)),
            Seq(new Expression(ScalaPsiUtil.undefineSubstitutor(typeParams).subst(subIntenal.inferValueType))),
            typeParams, shouldUndefineParameters = false)
          nonValueType = Success(ScTypePolymorphicType(m, update.typeParameters), Some(this)) //here should work in different way:
        }
        if (!fromUnderscoreSection) {
          updateRes(expectedType.get)
        } else {
          expectedType.get match {
            case ScFunctionType(retType, _) => updateRes(retType)
            case p: ScParameterizedType => p.getFunctionType match {
              case Some(ScFunctionType(retType, _)) => updateRes(retType)
              case _ =>
            }
            case _ => //do not update res, we haven't expected type
          }
        }

      }
      case _ =>
    }
    nonValueType
  }

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    var nonValueType: TypeResult[ScType] = getEffectiveInvokedExpr.getNonValueType(TypingContext.empty)
    nonValueType = updateAccordingToExpectedType(nonValueType)

    def tuplizyCase(fun: (Seq[Expression]) => (ScType, scala.Seq[ApplicabilityProblem], Seq[(Parameter, ScExpression)]),
                    exprs: Seq[Expression]): ScType = {
      val c = fun(exprs)
      def tail: ScType = {
        applicabilityProblemsVar = c._2
        matchedParametersVar = c._3

        c._1
      }
      if (!c._2.isEmpty) {
        ScalaPsiUtil.tuplizy(exprs, getProject, getResolveScope) match {
          case Some(e) => {
            val cd = fun(e)
            if (!cd._2.isEmpty) tail
            else {
              applicabilityProblemsVar = cd._2
              matchedParametersVar = cd._3
              cd._1
            }
          }
          case _ => tail
        }

      } else tail
    }

    val res: ScType = nonValueType match {
      case Success(ScFunctionType(retType: ScType, params: Seq[ScType]), _) => {
        val exprs: Seq[Expression] = argumentExpressions.map(Expression(_))
        def fun(t: Seq[Expression]) = {
          val conformanceExt = Compatibility.checkConformanceExt(true, params.zipWithIndex.map {
            case (tp, i) => {
              new Parameter("v" + (i + 1), tp, false, false, false)
            }
          }, t, true, false)
          (retType, conformanceExt.problems, conformanceExt.matchedArgs)
        }
        tuplizyCase(fun, exprs)
      }
      case Success(ScMethodType(retType, params, _), _) => {
        val exprs: Seq[Expression] = argumentExpressions.map(Expression(_))
        def fun(t: Seq[Expression]) = {
          val conformanceExt = Compatibility.checkConformanceExt(true, params, t, true, false)
          (retType, conformanceExt.problems, conformanceExt.matchedArgs)
        }
        tuplizyCase(fun, exprs)
      }
      case Success(ScTypePolymorphicType(ScMethodType(retType, params, _), typeParams), _) => {
        val exprs: Seq[Expression] = argumentExpressions.map(expr => new Expression(expr))
        def fun(t: Seq[Expression]) = ScalaPsiUtil.localTypeInferenceWithApplicabilityExt(retType, params, t, typeParams)
        tuplizyCase(fun, exprs)
      }
      case Success(ScTypePolymorphicType(ScFunctionType(retType, params), typeParams), _) => {
        val exprs: Seq[Expression] = argumentExpressions.map(expr => new Expression(expr))
        def fun(t: Seq[Expression]) =
          ScalaPsiUtil.localTypeInferenceWithApplicabilityExt(retType, params.zipWithIndex.map {
            case (tp, i) => new Parameter("v" + (i + 1), tp, false, false, false)
          }, t, typeParams)
        tuplizyCase(fun, exprs)
      }
      case Success(tp: ScType, _) if this.isInstanceOf[ScMethodCall] => //todo: remove reference to method call
        ScalaPsiUtil.processTypeForUpdateOrApply(tp, this.asInstanceOf[ScMethodCall], false).getOrElse(Nothing) match {
          case ScFunctionType(retType: ScType, params: Seq[ScType]) => {
            val exprs: Seq[Expression] = argumentExpressionsIncludeUpdateCall.map(Expression(_))
            def fun(t: Seq[Expression]) = {
              val conformanceExt = Compatibility.checkConformanceExt(true, params.zipWithIndex.map {
                case (paramType, i) => new Parameter("v" + (i + 1), paramType, false, false, false)
              }, t, true, false)
              (retType, conformanceExt.problems, conformanceExt.matchedArgs)
            }
            tuplizyCase(fun, exprs)
          }
          case ScMethodType(retType, params, _) => {
            val exprs: Seq[Expression] = argumentExpressionsIncludeUpdateCall.map(Expression(_))
            def fun(t: Seq[Expression]) = {
              val conformanceExt = Compatibility.checkConformanceExt(true, params, t, true, false)
              (retType, conformanceExt.problems, conformanceExt.matchedArgs)
            }
            tuplizyCase(fun, exprs)
          }
          case ScTypePolymorphicType(ScMethodType(retType, params, _), typeParams) => {
            val exprs: Seq[Expression] = argumentExpressionsIncludeUpdateCall.map(expr => new Expression(expr))
            def fun(t: Seq[Expression]) = ScalaPsiUtil.localTypeInferenceWithApplicabilityExt(retType, params, t, typeParams)
            tuplizyCase(fun, exprs)
          }
          case ScTypePolymorphicType(ScFunctionType(retType, params), typeParams) => {
            val exprs: Seq[Expression] = argumentExpressionsIncludeUpdateCall.map(expr => new Expression(expr))
            def fun(t: Seq[Expression]) = {
              val params1 = params.zipWithIndex.map {
                case (paramType, i) => new Parameter("v" + (i + 1), paramType, false, false, false)
              }
              ScalaPsiUtil.localTypeInferenceWithApplicabilityExt(retType, params1, t, typeParams)
            }
            tuplizyCase(fun, exprs)
          }
          case typeAfterUpdateProcess => {
            applicabilityProblemsVar = Seq(new DoesNotTakeParameters)
            matchedParametersVar = Seq()
            typeAfterUpdateProcess
          }
      }
      case _ => return nonValueType
    }

    Success(res, Some(this))
  }

  private var applicabilityProblemsVar: Seq[ApplicabilityProblem] = Seq.empty
  private var matchedParametersVar: Seq[(Parameter, ScExpression)] = Seq.empty


}