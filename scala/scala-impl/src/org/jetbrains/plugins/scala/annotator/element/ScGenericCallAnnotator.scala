package org.jetbrains.plugins.scala.annotator.element

import com.intellij.psi.impl.light.LightDefaultConstructor
import com.intellij.psi.{PsiMethod, PsiNamedElement, PsiTypeParameterListOwner}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.quickfix.ImportNamedTypeArgumentsFeatureFlagQuickFix
import org.jetbrains.plugins.scala.extensions.{ObjectExt, _}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScalaConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeArgument, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScGenericCall, ScParenthesisedExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ApplyOrUpdateInvocation
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.{PsiTypeParameterListOwnerExt, PsiTypeParametersExt, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.{Context, DefaultTypeParameterMismatch, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project._

import scala.annotation.tailrec


object ScGenericCallAnnotator extends ElementAnnotator[ScGenericCall] {
  private def typeParamsFromInnerApplyCall(srr: ScalaResolveResult): Seq[TypeParameter] =
    if (srr.name == CommonNames.Apply)
      srr.innerResolveResult match {
        case Some(ScalaResolveResult(f: ScTypeParametersOwner, _))     => f.typeParameters.map(TypeParameter(_))
        case Some(ScalaResolveResult(f: PsiTypeParameterListOwner, _)) => f.getTypeParameters.instantiate
        case _                                                         => Seq.empty
      }
    else Seq.empty

  private def typeArgClauseIndex(genericCall: ScGenericCall): Int = {
    @tailrec
    def countNestedTypeArgClauses(expr: ScExpression, acc: Int): Int = expr match {
      case gen: ScGenericCall           => countNestedTypeArgClauses(gen.referencedExpr, acc + 1)
      case invocation: MethodInvocation => countNestedTypeArgClauses(invocation.getInvokedExpr, acc)
      case _                            => acc
    }

    countNestedTypeArgClauses(genericCall.referencedExpr, 0)
  }

  @tailrec
  private def referenceTargetDeep(e: ScExpression): ScExpression = e match {
    case gen: ScGenericCall         => referenceTargetDeep(gen.referencedExpr)
    case inv: MethodInvocation      => referenceTargetDeep(inv.getEffectiveInvokedExpr)
    case paren: ScParenthesisedExpr => paren.innerElement match {
      case None        => paren
      case Some(inner) => referenceTargetDeep(inner)
    }
    case _ => e
  }

  override def annotate(genCall: ScGenericCall, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit = {
    implicit val context: Context = Context(genCall)

    val typeArgs = genCall.typeArgs
    if (genCall.isInScala3File && typeArgs.hasNamedTypeArgs) {
      ScParameterizedTypeElementAnnotator.annotateDuplicatedNamedTypeArguments(typeArgs)

      if (!genCall.isNamedTypeArgumentsFeatureImported) {
        typeArgs.namedTypeArgs.headOption.flatMap(_.nameElement).foreach { firstNamedTypeArgName =>
          holder.createErrorAnnotation(
            firstNamedTypeArgName,
            ScalaBundle.message("named.type.arguments.require.language.experimental.named.type.arguments"),
            new ImportNamedTypeArgumentsFeatureFlagQuickFix(firstNamedTypeArgName)
          )
        }
      }
    }

    if (typeAware) {
      for {
        ref <- genCall.referencedExpr.asOptionOf[ScReferenceExpression] //@TODO: interleaved clauses
        rr  <- ref.bind()
      } {
        val f =
          if (ApplyOrUpdateInvocation.innerSrrHasTypeParameters(rr))
            rr.innerResolveResult.get.element
          else rr.element

        val typeArgClauseIdx = typeArgClauseIndex(genCall)

        if (f.is[ScFunction, PsiMethod, ScSyntheticFunction]) {
          rr.problems.foreach {
            case DefaultTypeParameterMismatch(expected, actual) =>
              holder.createErrorAnnotation(
                genCall.typeArgs,
                ScalaBundle.message("type.mismatch.default.args.expected.actual", expected, actual))
            case _ =>
          }
        }

        f match {
          case typeParamOwner: PsiNamedElement with PsiTypeParameterListOwner if !isKindProjector(genCall) =>
            val typeParams = f match {
              case ScalaConstructor(cons) =>
                cons
                  .getClassTypeParameters
                  .map(
                    _.typeParameters.map(TypeParameter(_))
                  ).getOrElse(Seq.empty)
              case other                  =>
                val clauseTypeParams = other match {
                  case fun: ScFunction =>
                    val extension =
                      if (!rr.isExtensionCall) fun.extensionMethodOwner.orElse(rr.exportedInExtension)
                      else                     None

                    val functionTypeParamsByClause = fun.typeParametersByClause
                    val extensionTypeParams        = extension.toSeq.flatMap(_.typeParameters.map(TypeParameter(_)))

                    if (extensionTypeParams.nonEmpty && typeArgClauseIdx == 0) extensionTypeParams
                    else {
                      val functionClauseIdx =
                        if (extensionTypeParams.nonEmpty) typeArgClauseIdx - 1
                        else                              typeArgClauseIdx

                      functionTypeParamsByClause.lift(functionClauseIdx).getOrElse(Seq.empty)
                    }
                  case lCons: LightDefaultConstructor =>
                    if (typeArgClauseIdx == 0) lCons.containingClass.getTypeParameters.instantiate
                    else                       Seq.empty
                  case jmethod: PsiMethod =>
                    if (jmethod.isConstructor) {
                      if (typeArgClauseIdx == 0) jmethod.containingClass.getTypeParameters.instantiate
                      else                       Seq.empty
                    } else jmethod.typeParametersByClause.lift(typeArgClauseIdx).getOrElse(Seq.empty)
                  case _ =>
                    typeParamOwner.typeParametersByClause.lift(typeArgClauseIdx).getOrElse(Seq.empty)
                }

                if (clauseTypeParams.isEmpty && typeArgClauseIdx == 0) typeParamsFromInnerApplyCall(rr)
                else                                                   clauseTypeParams
            }

            val stringPresentation = s"method ${typeParamOwner.name}"
            implicit val tpc: TypePresentationContext = typeParamOwner

            ScParameterizedTypeElementAnnotator.annotateTypeArgs[ScTypeArgument](
              typeParams,
              genCall.typeArguments,
              genCall.typeArgs.getTextRange,
              rr.substitutor,
              stringPresentation,
              _.`type`()
            )
          case _ =>
        }
      }
    }
  }

  private def isKindProjector(genericCall: ScGenericCall): Boolean =
    genericCall.kindProjectorPluginEnabled && {
      val refText = genericCall.referencedExpr.getText
      refText == "Lambda" || refText == "λ"
    }

}
