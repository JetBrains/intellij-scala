package org.jetbrains.plugins.scala.annotator.element

import com.intellij.psi.impl.compiled.ClsMethodImpl
import com.intellij.psi.impl.light.LightDefaultConstructor
import com.intellij.psi.{PsiMethod, PsiNamedElement, PsiTypeParameter, PsiTypeParameterListOwner}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions.{ObjectExt, _}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScalaConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ApplyOrUpdateInvocation
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.{PsiTypeParametersExt, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.{Context, DefaultTypeParameterMismatch, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult


object ScGenericCallAnnotator extends ElementAnnotator[ScGenericCall] {
  private def typeParamsFromInnerApplyCall(srr: ScalaResolveResult): Seq[TypeParameter] =
    if (srr.name == CommonNames.Apply)
      srr.innerResolveResult match {
        case Some(ScalaResolveResult(f: ScTypeParametersOwner, _))     => f.typeParameters.map(TypeParameter(_))
        case Some(ScalaResolveResult(f: PsiTypeParameterListOwner, _)) => f.getTypeParameters.instantiate
        case _                                                         => Seq.empty
      }
    else Seq.empty

  override def annotate(genCall: ScGenericCall, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit = {
    implicit val context: Context = Context(genCall)

    if (typeAware) {
      for {
        ref <- genCall.referencedExpr.asOptionOf[ScReferenceExpression]
        rr  <- ref.bind()
      } {
        val f =
          if (ApplyOrUpdateInvocation.innerSrrHasTypeParameters(rr))
            rr.innerResolveResult.get.element
          else rr.element


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
                val tparams = other match {
                  case fun: ScFunction =>
                    val extension = fun.extensionMethodOwner.orElse(rr.exportedInExtension).filter(_ => !rr.isExtensionCall)
                    extension.fold(fun.typeParameters)(_.typeParameters)
                  case lCons: LightDefaultConstructor =>
                    lCons.containingClass.getTypeParameters.toSeq
                  case jmethod: PsiMethod =>
                    if (jmethod.isConstructor) jmethod.containingClass.getTypeParameters.toSeq
                    else jmethod.getTypeParameters.toSeq
                  case _ => typeParamOwner.getTypeParameters.toSeq
                }

                if (tparams.isEmpty) typeParamsFromInnerApplyCall(rr)
                else                 tparams.map(TypeParameter(_))
            }

            val stringPresentation = s"method ${typeParamOwner.name}"
            implicit val tpc: TypePresentationContext = typeParamOwner

            ScParameterizedTypeElementAnnotator.annotateTypeArgs[ScTypeElement](
              typeParams,
              genCall.arguments,
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
