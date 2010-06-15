package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import types.result.{Success, TypeResult, Failure, TypingContext}
import resolve.ScalaResolveResult
import types.{ScSubstitutor, ScParameterizedType, ScFunctionType, ScType}
import com.intellij.psi.{PsiElement, PsiMethod, PsiClass}

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
        getContext match {
          case typed: ScTypedStmt => {
            typed.typeElement match {
              case Some(_) => return typed.getType(ctx)
              case _ => return Failure("Typed statement is not complete for underscore section", Some(this))
            }
          }
          case _ =>
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
            val i = unders.findIndexOf(_.getTextRange.getStartOffset == startOffset)
            var result: Option[ScType] = null //strange logic to handle problems with detecting type
            var forEqualsParamLength: Boolean = false //this is for working completion
            for (tp <- expr.expectedTypes if result != None) {
              tp match {
                case ScFunctionType(_, params) if params.length >= unders.length => {
                  if (result != null) {
                    if (params.length == unders.length && !forEqualsParamLength) {
                      result = Some(params(i))
                      forEqualsParamLength = true
                    } else if (params.length == unders.length) result = None
                  }
                  else if (params.length > unders.length) result = Some(params(i))
                  else {
                    try
                    result = Some(params(i))
                    catch {
                      case e: Exception => {
                        "stop"
                      }
                    }
                    forEqualsParamLength = true
                  }
                }
                case ScParameterizedType(t, args) => {
                  ScType.extractDesignated(t) match { //todo: this is hack, scala.Function1 ScProjectionType?
                    case Some((c: PsiClass, _)) if c.getQualifiedName.startsWith("scala.Function") &&
                            args.length >= unders.length + 1 => {
                      if (result != null) {
                        if (args.length == unders.length + 1 && !forEqualsParamLength) {
                          result = Some(args(i))
                          forEqualsParamLength = true
                        } else if (args.length == unders.length + 1) result = None
                      }
                      else if (args.length > unders.length + 1) result = Some(args(i))
                      else {
                        result = Some(args(i))
                        forEqualsParamLength = true
                      }
                    }
                    case _ =>
                  }
                }
                case _ =>
              }
            }
            if (result == null) result = None
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