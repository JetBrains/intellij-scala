package org.jetbrains.plugins.scala.externalLibraries

import org.jetbrains.plugins.scala.externalLibraries.kindProjector.KindProjectorUtil.{Lambda, LambdaSymbolic}
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition

package object kindProjector {
  object TypeLambda {
    def unapply(projection: ScTypeProjection): Option[ScTypeAliasDefinition] =
      projection.typeElement match {
        case ScParenthesisedTypeElement(ScCompoundTypeElement(Seq(), Some(refinement))) =>
          refinement.types match {
            case Seq(alias: ScTypeAliasDefinition) if alias.name == projection.refName =>
              Option(alias)
            case _ => None
          }
        case _ => None
      }
  }

  object PolymorphicLambda {
    private[this] val polyLambdaIds = Seq(Lambda, LambdaSymbolic)

    def unapply(gc: ScGenericCall): Option[(ScTypeElement, ScTypeElement, ScTypeElement)] =
      if (gc.kindProjectorPluginEnabled) {
        gc.referencedExpr match {
          case ref: ScReferenceExpression
              if !ref.isQualified && polyLambdaIds.contains(ref.getText) =>
            gc.arguments match {
              case Seq(infix @ ScInfixTypeElement(lhs, _, Some(rhs))) => Option((infix, lhs, rhs))
              case Seq(tpe @ ScParameterizedTypeElement(_, Seq(lhs, rhs))) =>
                Option((tpe, lhs, rhs))
              case _ => None
            }
          case _ => None
        }
      } else None
  }
}
