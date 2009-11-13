package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.statements.params.{ScParameters, ScParameter}
import api.statements.{ScFun, ScFunction}
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import api.expr._
import result.{Success, TypingContext}
import api.toplevel.typedef.ScClass
import api.base.types.ScSequenceArg
import com.intellij.psi._

/**
 * @author ven
 */
object Compatibility {
  def compatible(l : ScType, r : ScType): Boolean = {
    if (r conforms l) {
      true
    }
    else {
      false //todo check view applicability
    }
  }

  private case class Parameter(name: String, tp: () => ScType, isDefault: Boolean, isRepeated: Boolean)

  private def checkConformance(checkNames: Boolean,
                               parameters: Seq[Parameter],
                               exprs: Seq[ScExpression]): (Boolean, ScUndefinedSubstitutor) = {
    var undefSubst = new ScUndefinedSubstitutor
    if (parameters.length == 0) return (exprs.length == 0, undefSubst)
    var k = 0
    var namedMode = false //todo: optimization, when namedMode enabled, exprs.length <= parameters.length
    val used = new Array[Boolean](parameters.length)
    while (k < parameters.length.min(exprs.length)) {
      def doNoNamed(expr: ScExpression): Boolean = {
        if (namedMode) {
          return false
        }
        else {
          val getIt = used.indexOf(false)
          used(getIt) = true
          val param: Parameter = parameters(getIt)
          val paramType = param.tp()
          (for (exprType <- expr.getTypeAfterImplicitConversion(Some(paramType))._1) yield {
            if (!Conformance.conforms(paramType, exprType)) false
            else {
              undefSubst += Conformance.undefinedSubst(paramType, exprType)
              true
            }
          }).getOrElse(true)
        }
      }

      exprs(k) match {
        case expr: ScTypedStmt if expr.getLastChild.isInstanceOf[ScSequenceArg] => {
          val seqClass: PsiClass = JavaPsiFacade.getInstance(expr.getProject).findClass("scala.collection.Seq", expr.getResolveScope)
          if (seqClass != null) {
            val getIt = used.indexOf(false)
            used(getIt) = true
            val param: Parameter = parameters(getIt)
            if (!param.isRepeated) return (false, undefSubst)
            val paramType = param.tp()
            val tp = ScParameterizedType(ScDesignatorType(seqClass), Seq(paramType))
            for (exprType <- expr.getTypeAfterImplicitConversion(Some(tp))._1) yield {
              if (!Conformance.conforms(tp, exprType)) return (false, undefSubst)
              else {
                undefSubst += Conformance.undefinedSubst(tp, exprType)
              }
            }
          } else if (!doNoNamed(expr)) return (false, undefSubst)
        }
        case assign@NamedAssignStmt(name) => {
          val ind = parameters.findIndexOf(_.name == name)
          if (ind == -1 || used(ind) == true) {
            if (!doNoNamed(assign)) return (false, undefSubst)
          }
          else {
            if (!checkNames) return (false, undefSubst)
            namedMode = true
            used(ind) = true
            val param: Parameter = parameters(ind)
            assign.getRExpression match {
              case Some(expr: ScExpression) => {
                val paramType = param.tp()
                for (exprType <- expr.getTypeAfterImplicitConversion(Some(paramType))._1)
                if (!Conformance.conforms(paramType, exprType)) return (false, undefSubst)
                else undefSubst += Conformance.undefinedSubst(paramType, exprType)
              }
              case _ => return (false, undefSubst)
            }
          }
        }
        case expr: ScExpression => {
          if (!doNoNamed(expr)) return (false, undefSubst)
        }
      }
      k = k + 1
    }
    if (exprs.length == parameters.length) return (true, undefSubst)
    else if (exprs.length > parameters.length) {
      if (namedMode) return (false, undefSubst)
      if (!parameters.last.isRepeated) return (false, undefSubst)
      val paramType: ScType = parameters.last.tp()
      while (k < exprs.length) {
        for (exprType <- exprs(k).getTypeAfterImplicitConversion(Some(paramType))._1) {
          if (!Conformance.conforms(paramType, exprType)) return (false, undefSubst)
          else undefSubst = Conformance.undefinedSubst(paramType, exprType)
        }
        k = k + 1
      }
    }
    else {
      if (exprs.length == parameters.length - 1 && !namedMode && parameters.last.isRepeated) return (true, undefSubst)
      for ((parameter: Parameter, b) <- parameters.zip(used)) {
        if (!b && !parameter.isDefault) {
          return (false, undefSubst)
        }
      }
    }
    return (true, undefSubst)
  }

  def compatible(named: PsiNamedElement, substitutor: ScSubstitutor,
                 argClauses: List[Seq[ScExpression]]): (Boolean, ScUndefinedSubstitutor) = {
    val exprs: Seq[ScExpression] = argClauses.headOption match {case Some(seq) => seq case _ => Seq.empty}
    named match {
      case synthetic: ScSyntheticFunction => {
        checkConformance(false, synthetic.paramTypes.map {tp: ScType => Parameter("", () => substitutor.subst(tp), false, false)}, exprs)
      }
      case fun: ScFunction => {
        val parameters: Seq[ScParameter] =
          if (fun.paramClauses.clauses.length == 0) Seq.empty
          else fun.paramClauses.clauses.apply(0).parameters
        checkConformance(true, parameters.map{param: ScParameter => Parameter(param.getName, () => {
          substitutor.subst(param.getType(TypingContext.empty).getOrElse(Nothing))
        }, param.isDefaultParam, param.isRepeatedParameter)}, exprs)
      }
      case method: PsiMethod => {
        val parameters: Seq[PsiParameter] = method.getParameterList.getParameters.toSeq
        checkConformance(false, parameters.map {param: PsiParameter => Parameter(param.getName, () => {
          substitutor.subst(ScType.create(param.getType, method.getProject))
        }, false, param.isVarArgs)}, exprs)
      }
      case cc: ScClass if cc.isCase => {
        val parameters: Seq[ScParameter] = {
          cc.clauses match {
            case Some(params: ScParameters) if params.clauses.length != 0 => params.clauses.apply(0).parameters
            case _ => Seq.empty
          }
        }
        checkConformance(true, parameters.map{param: ScParameter => Parameter(param.getName, () => {
          substitutor.subst(param.getType(TypingContext.empty).getOrElse(Nothing))
        }, param.isDefaultParam, param.isRepeatedParameter)}, exprs)
      }

      case _ => (false, new ScUndefinedSubstitutor)
    }
  }
}
