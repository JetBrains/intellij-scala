package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * @author Alexander Podkhalyuzin, ilyas
 */

class ScUnderscoreSectionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScUnderscoreSection {
  override def toString: String = "UnderscoreSection"

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    bindingExpr match {
      case Some(ref: ScReferenceExpression) =>
        def fun(): TypeResult[ScType] = {
          ref.getNonValueType(TypingContext.empty).map {
            case ScTypePolymorphicType(internalType, typeParameters) =>
              ScTypePolymorphicType(ScMethodType(internalType, Nil, isImplicit = false)(getProject, getResolveScope), typeParameters)
            case tp: ScType => ScMethodType(tp, Nil, isImplicit = false)(getProject, getResolveScope)
          }
        }
        ref.bind() match {
          case Some(ScalaResolveResult(f: ScFunction, _)) if f.paramClauses.clauses.length == 0 => fun()
          case Some(ScalaResolveResult(c: ScClassParameter, _)) if c.isVal | c.isVar => fun()
          case Some(ScalaResolveResult(b: ScBindingPattern, _)) =>
            b.nameContext match {
              case _: ScValue | _: ScVariable if b.isClassMember => fun()
              case v: ScValue if v.hasModifierPropertyScala("lazy") => fun()
              case _ => ref.getNonValueType(TypingContext.empty)
            }
          case Some(ScalaResolveResult(p: ScParameter, _)) if p.isCallByNameParameter => fun()
          case _ => ref.getNonValueType(TypingContext.empty)
        }
      case Some(expr) => expr.getNonValueType(TypingContext.empty)
      case None =>
        getContext match {
          case typed: ScTypedStmt =>
            overExpr match {
              case Some(`typed`) =>
                typed.typeElement match {
                  case Some(te) => return te.getType(TypingContext.empty)
                  case _ => return Failure("Typed statement is not complete for underscore section", Some(this))
                }
              case _ => return typed.getType(TypingContext.empty)
            }
          case _ =>
        }
        overExpr match {
          case None => Failure("No type inferred", None)
          case Some(expr: ScExpression) =>
            val unders = ScUnderScoreSectionUtil.underscores(expr)
            var startOffset = if (expr.getTextRange != null) expr.getTextRange.getStartOffset else 0
            var e: PsiElement = this
            while (e != expr) {
              startOffset += e.startOffsetInParent
              e = e.getContext
            }
            val i = unders.indexWhere(_.getTextRange.getStartOffset == startOffset)
            if (i < 0) return Failure("Not found under", None)
            var result: Option[ScType] = null //strange logic to handle problems with detecting type
            var forEqualsParamLength: Boolean = false //this is for working completion
            for (tp <- expr.expectedTypes(fromUnderscore = false) if result != None) {

              def processFunctionType(params: Seq[ScType]) {
                if (result != null) {
                  if (params.length == unders.length && !forEqualsParamLength) {
                    result = Some(params(i))
                    forEqualsParamLength = true
                  } else if (params.length == unders.length) result = None
                }
                else if (params.length > unders.length) result = Some(params(i))
                else {
                  result = Some(params(i))
                  forEqualsParamLength = true
                }
              }

              tp.removeAbstracts match {
                case ScFunctionType(_, params) if params.length >= unders.length => processFunctionType(params)
                case any if ScalaPsiUtil.isSAMEnabled(this) =>
                  ScalaPsiUtil.toSAMType(any, getResolveScope) match {
                    case Some(ScFunctionType(_, params)) if params.length >= unders.length =>
                      processFunctionType(params)
                    case _ =>
                  }
                case _ =>
              }
            }
            if (result == null || result == None) {
              expectedType(fromUnderscore = false) match {
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

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitUnderscoreExpression(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitUnderscoreExpression(this)
      case _ => super.accept(visitor)
    }
  }
}