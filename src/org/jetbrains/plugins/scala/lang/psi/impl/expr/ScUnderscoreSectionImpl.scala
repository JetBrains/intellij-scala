package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import types.result.{Success, TypeResult, Failure, TypingContext}
import com.intellij.psi.PsiElement
import types._

/**
 * @author Alexander Podkhalyuzin, ilyas
 */

class ScUnderscoreSectionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScUnderscoreSection {
  override def toString: String = "UnderscoreSection"

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    bindingExpr match {
      case Some(x) => {
        x.getNonValueType(TypingContext.empty)
      }
      case None => {
        expectedType(false) match {
          case Some(exp) => return Success(exp, Some(this))
          case None =>
        }
        overExpr match {
          case None => Failure("No type inferred", None)
          case Some(expr: ScExpression) => {
            val unders = ScUnderScoreSectionUtil.underscores(expr)
            var startOffset = if (expr.getTextRange != null) expr.getTextRange.getStartOffset else 0
            var e: PsiElement = this
            while (e != expr) {
              startOffset += e.getStartOffsetInParent
              e = e.getContext
            }
            val i = unders.indexWhere(_.getTextRange.getStartOffset == startOffset)
            if (i < 0) return Failure("Not found under", None)
            var result: Option[ScType] = null //strange logic to handle problems with detecting type
            var forEqualsParamLength: Boolean = false //this is for working completion
            for (tp <- expr.expectedTypes(false) if result != None) {

              def processFunctionType(tp: ScFunctionType) {
                import tp.params
                if (result != null) {
                  if (params.length == unders.length && !forEqualsParamLength) {
                    result = Some(params(i).removeAbstracts)
                    forEqualsParamLength = true
                  } else if (params.length == unders.length) result = None
                }
                else if (params.length > unders.length) result = Some(params(i).removeAbstracts)
                else {
                  result = Some(params(i).removeAbstracts)
                  forEqualsParamLength = true
                }
              }

              ScType.extractFunctionType(tp) match {
                case Some(ft @ ScFunctionType(_, params)) if params.length >= unders.length => processFunctionType(ft)
                case _ =>
              }
            }
            if (result == null) {
              expectedType(false) match {
                case Some(tp: ScType) => result = Some(tp)
                case _ => result = None
              }
            }
            result match {
              case None => Failure("No type inferred", None)
              case Some(t) => Success(t, None)
            }
          }
        }
      }
    }
  }
}