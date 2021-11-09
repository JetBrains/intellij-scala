package org.jetbrains.plugins.scala.annotator.element

import com.intellij.psi.{PsiMethod, PsiNamedElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions.{ObjectExt, _}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScalaConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.DefaultTypeParameterMismatch
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter


object ScGenericCallAnnotator extends ElementAnnotator[ScGenericCall] {
  override def annotate(genCall: ScGenericCall, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit = {
    if (typeAware) {
      for {
        ref <- genCall.referencedExpr.asOptionOf[ScReferenceExpression]
        rr  <- ref.bind()
        f = rr.element
      } {
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
          case typeParamOwner: PsiNamedElement with ScTypeParametersOwner if !isKindProjector(genCall) =>
            val typeParams = (f match {
              case ScalaConstructor(cons) => cons.getClassTypeParameters.map(_.typeParameters).getOrElse(Seq.empty)
              case _                      => typeParamOwner.typeParameters
            }).map(TypeParameter(_))

            val stringPresentation = s"method ${typeParamOwner.name}"

            ScParameterizedTypeElementAnnotator.annotateTypeArgs(
              typeParams,
              genCall.typeArgs,
              rr.substitutor,
              stringPresentation
            )
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
