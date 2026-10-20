package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, _}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ParameterExpectedTypesUtil._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TupleType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

class ScUnderscoreSectionImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScUnderscoreSection {
  protected override def innerType(expectedType: Option[ScType]): TypeResult = {
    bindingExpr match {
      // the expression of a method value `foo _` has no expected type
      case Some(ref: ScReferenceExpression) =>
        def fun(): TypeResult =
          ref.getNonValueType(expectedType = None).map {
            case ScTypePolymorphicType(internalType, typeParameters) =>
              ScTypePolymorphicType(
                ScMethodType(internalType, Nil),
                typeParameters
              )
            case tp: ScType => ScMethodType(tp, Nil)
          }

        ref.bind() match {
          case Some(ScalaResolveResult(f: ScFunction, _)) if f.paramClauses.clauses.isEmpty => fun()
          case Some(ScalaResolveResult(c: ScClassParameter, _)) if c.isVal | c.isVar => fun()
          case Some(ScalaResolveResult(b: ScBindingPattern, _)) =>
            b.nameContext match {
              case _: ScValue | _: ScVariable if b.isClassMember => fun()
              case v: ScValue if v.hasModifierPropertyScala("lazy") => fun()
              case _ => ref.getNonValueType(expectedType = None)
            }
          case Some(ScalaResolveResult(p: ScParameter, _)) if p.isCallByNameParameter => fun()
          case _ => ref.getNonValueType(expectedType = None)
        }
      case Some(expr) => expr.getNonValueType(expectedType = None)
      case None =>
        getContext match {
          case typed: ScTypedExpression =>
            overExpr match {
              case Some(`typed`) =>
                typed.typeElement match {
                  case Some(te) => return te.`type`()
                  case _        => return Failure(ScalaPsiBundle.message("typed.statement.is.not.complete.for.underscore.section"))
                }
              case _ => return typed.`type`()
            }
          case _ =>
        }

        overExpr match {
          case None                     => Failure(ScalaPsiBundle.message("no.type.inferred.for.unknown.expression"))
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
              case -1 => Failure(ScalaPsiBundle.message("failed.to.found.corresponding.underscore.section"))
              case i =>
                val untuplingEnabled = this.isInScala3Module

                // A placeholder is a parameter of the anonymous function, so, like the parameters of function
                // literals (see ScParameter.expectedParamType), it is typed against the expected type computed
                // from the context of the function, since it is shared by all the references to it
                expr.expectedType(fromUnderscore = false).map(unwrapContextFunctionType(_)) match {
                  case Some(functionLikeType(_, _, params)) =>
                    val param = params match {
                      case Seq(TupleType(comps)) if untuplingEnabled && comps.size == unders.size =>
                        comps.lift(i)
                      case _ => params.lift(i)
                    }
                    val failure: TypeResult = Failure(ScalaPsiBundle.message("could.not.infer.type.of.underscore.section"))

                    param.fold(failure)(tpe =>
                      if (!isFullyDefined(tpe)) {
                        val ithUnderscore: Seq[ScExpression] => Option[ScExpression] =
                          _.filterByType[ScUnderscoreSection].lift(idx)

                        val maybeFromUndoingEtaExp = inferExpectedParamTypeUndoingEtaExpansion(expr, ithUnderscore)
                        maybeFromUndoingEtaExp.asTypeResult
                      } else Right(tpe.removeAbstracts)
                    )
                  case _ => expectedType.asTypeResult
                }
            }
        }
    }
  }

  override def toString: String = "UnderscoreSection"
}
