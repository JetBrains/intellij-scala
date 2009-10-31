package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.statements.params.{ScParameters, ScParameter}
import com.intellij.psi.{PsiParameter, PsiMethod}
import api.statements.{ScFun, ScFunction}
import impl.toplevel.synthetic.ScSyntheticFunction
import api.expr._
import result.{Success, TypingContext}

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
                               exprs: Seq[ScExpression]): Boolean = {
    if (parameters.length == 0) return exprs.length == 0
    var k = 0
    var namedMode = false //todo: optimization, when namedMode enabled, exprs.length <= parameters.length
    val used = new Array[Boolean](parameters.length)
    while (k < parameters.length.min(exprs.length)) {
      def doNoNamed(expr: ScExpression): Boolean = {
        if (namedMode) {
          return false
        }
        else (for (exprType <- expr.getType(TypingContext.empty)) yield {
          val getIt = used.indexOf(false)
          used(getIt) = true
          val param: Parameter = parameters(getIt)
          val paramType = param.tp()
          if (!exprType.conforms(paramType)) false
          else true
        }).getOrElse(true)
      }

      exprs(k) match {
        case assign@NamedAssignStmt(name) => {
          val ind = parameters.findIndexOf(_.name == name)
          if (ind == -1 || used(ind) == true) {
            if (!doNoNamed(assign)) return false
          }
          else {
            if (!checkNames) return false
            namedMode = true
            used(ind) = true
            val param: Parameter = parameters(ind)
            assign.getRExpression match {
              case Some(expr: ScExpression) => {
                for (exprType <- expr.getType(TypingContext.empty);
                     paramType = param.tp())
                if (!exprType.conforms(paramType)) return false
              }
              case _ => return false
            }
          }
        }
        case expr: ScExpression => {
          if (!doNoNamed(expr)) return false
        }
      }
      k = k + 1
    }
    if (exprs.length == parameters.length) return true
    else if (exprs.length > parameters.length) {
      if (namedMode) return false
      if (!parameters.last.isRepeated) return false
      val paramType: ScType = parameters.last.tp()
      while (k < exprs.length) {
        for (exprType <- exprs(k).getType(TypingContext.empty)) {
          if (!exprType.conforms(paramType)) return false
        }
        k = k + 1
      }
    }
    else {
      if (exprs.length == parameters.length - 1 && !namedMode && parameters.last.isRepeated) return true
      for ((parameter: Parameter, b) <- parameters.zip(used)) {
        if (!b && !parameter.isDefault) {
          return false
        }
      }
    }
    return true
  }

  def compatible(sign: PhysicalSignature, argClauses: List[Seq[ScExpression]], curried : Boolean): Boolean = {
    sign.method match {
      case fun: ScFunction => {

        /*def checkClausesConformance(funType: ScType, argCls: List[Seq[ScExpression]]) : Boolean =
          funType match {
            case ScFunctionType(rt, params) => argCls match {
              case h :: t => Conformance.conformsSeq(params.map(sign.substitutor.subst(_)),
                                                     h.map(_.cachedType))
              case Nil => curried
            }
            case _ =>  false
        }

        checkClausesConformance(fun.functionType, argClauses)*/
        val exprs: Seq[ScExpression] = if (argClauses.isEmpty) Nil else argClauses.head

        val parameters: Seq[ScParameter] = fun.clauses match {
          case Some(params: ScParameters) => {
            if (params.clauses.length == 0) return exprs.length == 0
            params.clauses.apply(0).parameters
          }
          case None => return exprs.length == 0
        }



        checkConformance(true, parameters.map{param: ScParameter => Parameter(param.getName, () => {
          sign.substitutor.subst(param.getType(TypingContext.empty).getOrElse(Nothing))
        }, param.isDefaultParam, param.isRepeatedParameter)}, exprs)
      }


      case method: PsiMethod => {
        val parameters: Seq[PsiParameter] = method.getParameterList.getParameters.toSeq
        checkConformance(false, parameters.map {param: PsiParameter => Parameter(param.getName, () => {
          sign.substitutor.subst(ScType.create(param.getType, method.getProject))
        }, false, param.isVarArgs)}, if (argClauses.length == 0) Nil else argClauses.head)
      }
    }
  }

  def compatible(synthetic: ScSyntheticFunction, subst: ScSubstitutor, argClauses: List[Seq[ScExpression]]): Boolean = {
    checkConformance(false, synthetic.paramTypes.map {tp: ScType => Parameter("", () => subst.subst(tp), false, false)},
      if (argClauses.length == 0) Nil else argClauses.head)
  }
}
