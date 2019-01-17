package org.jetbrains.plugins.scala.codeInspection.typeLambdaSimplify

import org.jetbrains.plugins.scala.codeInspection.typeLambdaSimplify.KindProjectorSimplifyTypeProjectionInspection._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

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

  def simplifyTypeLambdasIn(e: ScalaPsiElement): Unit =
    e.accept(new ScalaRecursiveElementVisitor {
      override def visitTypeProjection(proj: ScTypeProjection): Unit = proj match {
        case TypeLambda(alias) =>
          val tparams = alias.typeParameters

          val (replacementText, toReplace) = proj.parent match {
            case Some(p: ScParameterizedTypeElement) if p.typeArgList.typeArgs.size == tparams.size =>
              (AppliedTypeLambdaCanBeSimplifiedInspection.simplifyTypeProjection(alias, p.typeArgList.typeArgs), p)
            case _ if tparams.nonEmpty && tparams.forall(canConvertBounds) =>
              (convertToKindProjIectorSyntax(alias), proj)
          }

          val simplified = ScalaPsiElementFactory.createTypeElementFromText(replacementText, e, null)
          toReplace.replace(simplified)
        case _ => super.visitTypeProjection(proj)
      }
    })
}
