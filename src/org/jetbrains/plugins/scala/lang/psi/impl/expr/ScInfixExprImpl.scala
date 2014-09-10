package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElementVisitor, PsiMethod, PsiParameter, PsiTypeParameter}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.Seq
import scala.collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScInfixExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScInfixExpr {
  override def toString: String = "InfixExpression"

  override def argumentExpressions: Seq[ScExpression] = {
    if (isLeftAssoc) Seq(lOp) else rOp match {
      case tuple: ScTuple => tuple.exprs
      case t: ScParenthesisedExpr => t.expr match {
        case Some(expr) => Seq(expr)
        case None => Seq(t)
      }
      case expr => Seq(expr)
    }
  }

  def possibleApplications: Array[Array[(String, ScType)]] = {
    val buffer = new ArrayBuffer[Array[(String, ScType)]]
    val ref = operation
    val variants = ref.getSameNameVariants
    val invocationCount = 1
    for (variant <- variants) {
      variant match {
        case ScalaResolveResult(method: PsiMethod, s: ScSubstitutor) =>
          val subst = if (method.getTypeParameters.length != 0) {
            val subst = method.getTypeParameters.foldLeft(ScSubstitutor.empty) {
              (subst, tp) => subst.bindT((tp.name, ScalaPsiUtil.getPsiElementId(tp)), ScUndefinedType(tp match {
                case tp: ScTypeParam => new ScTypeParameterType(tp: ScTypeParam, s)
                case tp: PsiTypeParameter => new ScTypeParameterType(tp, s)
              }))
            }
            s.followed(subst)
          }
          else s
          method match {
            case fun: ScFunction =>
              if (fun.paramClauses.clauses.length >= invocationCount) {
                buffer += fun.paramClauses.clauses.apply(invocationCount - 1).parameters.map({
                  p => (p.name,
                          subst.subst(p.getType(TypingContext.empty).getOrAny))
                }).toArray
              } else if (invocationCount == 1) buffer += Array.empty
            case method: PsiMethod if invocationCount == 1 =>
              buffer += method.getParameterList.getParameters.map({
                p: PsiParameter => ("", subst.subst(p.exactParamType()))
              })
            case _ =>
          }
        case _ => //todo: other options
      }
    }
    buffer.toArray
  }

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    def cacheBaseParts(inf: ScInfixExpr): Unit = {
      inf.getBaseExpr match {
        case inf: ScInfixExpr =>
          cacheBaseParts(inf)
        case _ =>
      }
      inf.getBaseExpr.getType(TypingContext.empty)
    }

    cacheBaseParts(this)

    operation.bind() match {
      //this is assignment statement: x += 1 equals to x = x + 1
      case Some(r) if r.element.name + "=" == operation.refName =>
        super.innerType(ctx)
        Success(Unit, Some(this))
      case _ => super.innerType(ctx)
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitInfixExpression(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitInfixExpression(this)
      case _ => super.accept(visitor)
    }
  }
}