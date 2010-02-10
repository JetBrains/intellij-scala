package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import java.lang.String
import api.statements.params.ScTypeParam
import types._
import types.result.TypingContext
import api.statements.ScFunction
import com.intellij.psi.{PsiParameter, PsiMethod, PsiTypeParameter}
import collection.mutable.ArrayBuffer
import resolve.ScalaResolveResult
import collection.Seq

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScInfixExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScInfixExpr with ScCallExprImpl {
  override def toString: String = "InfixExpression"

  def argumentExpressions: Seq[ScExpression] = {
    if (isLeftAssoc) Seq.singleton(lOp) else rOp match {
      case tuple: ScTuple => tuple.exprs
      case rOp => Seq.singleton(rOp)
    }
  }

  def possibleApplications: Array[Array[(String, ScType)]] = {
    val buffer = new ArrayBuffer[Array[(String, ScType)]]
    val ref = operation
    val variants = ref.getSameNameVariants
    val invocationCount = 1
    for (variant <- variants) {
      variant match {
        case ScalaResolveResult(method: PsiMethod, s: ScSubstitutor) => {
          val subst = if (method.getTypeParameters.length != 0) {
            val subst = method.getTypeParameters.foldLeft(ScSubstitutor.empty) {
              (subst, tp) => subst.bindT(tp.getName, ScUndefinedType(tp match {
                case tp: ScTypeParam => new ScTypeParameterType(tp: ScTypeParam, s)
                case tp: PsiTypeParameter => new ScTypeParameterType(tp, s)
              }))
            }
            s.followed(subst)
          }
          else s
          method match {
            case fun: ScFunction => {
              if (fun.paramClauses.clauses.length >= invocationCount) {
                buffer += fun.paramClauses.clauses.apply(invocationCount - 1).parameters.map({
                  p => (p.name,
                          subst.subst(p.getType(TypingContext.empty).getOrElse(Any)))
                }).toArray
              } else if (invocationCount == 1) buffer += Array.empty
            }
            case method: PsiMethod if invocationCount == 1 => {
              buffer += method.getParameterList.getParameters.map({
                p: PsiParameter => {
                  val tp: ScType = subst.subst(ScType.create(p.getType, p.getProject))
                  ("", if (!p.isVarArgs) tp else tp match {
                    case ScParameterizedType(_, args) if args.length == 1 => args(0)
                    case _ => tp
                  })
                }
              })
            }
            case _ =>
          }
        }
        case _ => //todo: other options
      }
    }
    buffer.toArray
  }
}