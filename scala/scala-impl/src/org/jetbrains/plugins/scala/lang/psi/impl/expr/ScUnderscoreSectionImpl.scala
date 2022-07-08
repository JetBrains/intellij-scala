package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, _}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ParameterExpectedTypesUtil._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

class ScUnderscoreSectionImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScUnderscoreSection {
  protected override def innerType: TypeResult = {
    bindingExpr match {
      case Some(ref: ScReferenceExpression) =>
        def fun(): TypeResult =
          ref.getNonValueType().map {
            case ScTypePolymorphicType(internalType, typeParameters) =>
              ScTypePolymorphicType(
                ScMethodType(internalType, Nil, isImplicit = false),
                typeParameters
              )
            case tp: ScType => ScMethodType(tp, Nil, isImplicit = false)
          }

        ref.bind() match {
          case Some(ScalaResolveResult(f: ScFunction, _)) if f.paramClauses.clauses.isEmpty => fun()
          case Some(ScalaResolveResult(c: ScClassParameter, _)) if c.isVal | c.isVar => fun()
          case Some(ScalaResolveResult(b: ScBindingPattern, _)) =>
            b.nameContext match {
              case _: ScValue | _: ScVariable if b.isClassMember => fun()
              case v: ScValue if v.hasModifierPropertyScala("lazy") => fun()
              case _ => ref.getNonValueType()
            }
          case Some(ScalaResolveResult(p: ScParameter, _)) if p.isCallByNameParameter => fun()
          case _ => ref.getNonValueType()
        }
      case Some(expr) => expr.getNonValueType()
      case None =>
        getContext match {
          case typed: ScTypedExpression =>
            overExpr match {
              case Some(`typed`) =>
                typed.typeElement match {
                  case Some(te) => return te.`type`()
                  case _        => return Failure(ScalaBundle.message("typed.statement.is.not.complete.for.underscore.section"))
                }
              case _ => return typed.`type`()
            }
          case _ =>
        }

        overExpr match {
          case None                     => Failure(ScalaBundle.message("no.type.inferred.for.unknown.expression"))
          case Some(expr: ScExpression) =>
            val unders      = ScUnderScoreSectionUtil.underscores(expr)
            var startOffset = if (expr.getTextRange != null) expr.getTextRange.getStartOffset else 0

            var e: PsiElement = this
            while (e != expr) {
              startOffset += e.startOffsetInParent
              e = e.getContext
            }

            val functionLikeType = FunctionLikeType(expr)

            val idx = unders.indexWhere(_.getTextRange.getStartOffset == startOffset)
            idx match {
              case -1 => Failure(ScalaBundle.message("failed.to.found.corresponding.underscore.section"))
              case i =>
                expr.expectedType(fromUnderscore = false) match {
                  case Some(functionLikeType(_, _, params)) =>
                    val param               = params.lift(i)
                    val failure: TypeResult = Failure(ScalaBundle.message("could.not.infer.type.of.underscore.section"))

                    param.fold(failure)(tpe =>
                      if (!isFullyDefined(tpe)) {
                        val ithUnderscore: Seq[ScExpression] => Option[ScExpression] =
                          _.filterByType[ScUnderscoreSection].lift(idx)

                        val maybeFromUndoingEtaExp = inferExpectedParamTypeUndoingEtaExpansion(expr, ithUnderscore)
                        maybeFromUndoingEtaExp.asTypeResult
                      } else Right(tpe.removeAbstracts)
                    )
                  case _ => this.expectedType(false).asTypeResult
                }
            }
        }
    }
  }

  override def toString: String = "UnderscoreSection"
}