package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import types._
import nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import result.{TypeResult, Success, TypingContext}
import types.Compatibility.Expression
import collection.Seq
import com.intellij.psi.PsiElementVisitor
import api.ScalaElementVisitor

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScMethodCallImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScMethodCall {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "MethodCall"

  //todo: refactor even more? see: ScExpression.*Type
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
    var nonValueType: TypeResult[ScType] = getInvokedExpr.getNonValueType(TypingContext.empty)
    nonValueType = updateAccordingToExpectedType(nonValueType)

    def tuplizyCase(fun: (Seq[Expression]) => (ScType, scala.Seq[ApplicabilityProblem], Seq[(Parameter, ScExpression)]),
                    exprs: Seq[Expression]): ScType = {
      val c = fun(exprs)
      def tail: ScType = {
        applicabilityProblemsVar = c._2
        matchedArgumentsVar = c._3

        c._1
      }
      if (!c._2.isEmpty) {
        ScalaPsiUtil.tuplizy(exprs, getProject, getResolveScope) match {
          case Some(e) => {
            val cd = fun(e)
            if (!cd._2.isEmpty) tail
            else {
              applicabilityProblemsVar = cd._2
              matchedArgumentsVar = cd._3
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
        def fun(t: Seq[Expression]) = ScalaPsiUtil.localTypeInferenceWithApplicabilityExt(retType, params.zipWithIndex.map {
          case (tp, i) => new Parameter("v" + (i + 1), tp, false, false, false)
        }, t, typeParams)
        tuplizyCase(fun, exprs)
      }
      case Success(tp: ScType, _) => ScalaPsiUtil.processTypeForUpdateOrApply(tp, this, false).getOrElse(Nothing) match {
        case ScFunctionType(retType: ScType, params: Seq[ScType]) => {
          val exprs: Seq[Expression] = argumentExpressionsIncludeUpdateCall.map(Expression(_))
          def fun(t: Seq[Expression]) = {
            val conformanceExt = Compatibility.checkConformanceExt(true, params.zipWithIndex.map {
              case (tp, i) => new Parameter("v" + (i + 1), tp, false, false, false)
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
              case (tp, i) => new Parameter("v" + (i + 1), tp, false, false, false)
            }
            ScalaPsiUtil.localTypeInferenceWithApplicabilityExt(retType, params1, t, typeParams)
          }
          tuplizyCase(fun, exprs)
        }
        case tp => {
          applicabilityProblemsVar = Seq(new DoesNotTakeParameters)
          matchedArgumentsVar = Seq()
          tp
        }
      }
      case x => return x
    }

    Success(res, Some(this))
  }

  private var applicabilityProblemsVar: Seq[ApplicabilityProblem] = Seq.empty
  private var matchedArgumentsVar: Seq[(Parameter, ScExpression)] = Seq.empty

  def applicationProblems: scala.Seq[ApplicabilityProblem] = {
    getType(TypingContext.empty) //update applicabilityProblemsVar if needed
    applicabilityProblemsVar
  }

  def matchedArguments: Map[ScExpression, Parameter] = {
    getType(TypingContext.empty) //update matchedArgumentsVar if needed
    matchedArgumentsVar.map{ case (a, b) => (b, a)}.toMap
  }
}