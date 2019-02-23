package org.jetbrains.plugins.scala.codeInspection.typeLambdaSimplify

import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition

object KindProjectorTypeLambdaUtil {
  object TypeLambda {
    def unapply(projection: ScTypeProjection): Option[ScTypeAliasDefinition] =
      if (projection.kindProjectorPluginEnabled) {
        projection.typeElement match {
          case parenType: ScParenthesisedTypeElement => parenType.innerElement match {
            case Some(ScCompoundTypeElement(Seq(), Some(refinement))) =>
              refinement.types match {
                case Seq(alias: ScTypeAliasDefinition) if alias.name == projection.refName => Option(alias)
                case _                                                                     => None
              }
            case _ => None
          }
          case _ => None
        }
      } else None
  }
}
