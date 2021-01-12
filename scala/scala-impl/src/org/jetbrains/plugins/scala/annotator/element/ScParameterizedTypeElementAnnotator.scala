package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiComment, PsiNamedElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext

object ScParameterizedTypeElementAnnotator extends ElementAnnotator[ScParameterizedTypeElement] {

  override def annotate(element: ScParameterizedTypeElement, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val typeParamOwner = element.typeElement.getTypeNoConstructor.toOption
      .flatMap(_.extractDesignated(expandAliases = false))
      .collect { case t: PsiNamedElement with ScTypeParametersOwner => t }
    typeParamOwner.foreach { typeParamOwner =>
      val params = typeParamOwner.typeParameters
      val typeArgList = element.typeArgList
      val args = typeArgList.typeArgs

      if (args.length < params.length) {
        // Annotate missing arguments
        val missing = params.drop(args.length)
        val missingText = missing.map(_.name).mkString(", ")
        val range = args.lastOption
          .map(e => new TextRange(e.endOffset - 1, typeArgList.endOffset))
          .getOrElse(typeArgList.getTextRange)
        holder.createErrorAnnotation(range, ScalaBundle.message("unspecified.type.parameters", missingText))
      } else if (args.length > params.length) {
        // Annotate to many arguments
        val firstExcessiveArg = args(params.length)
        val opening = firstExcessiveArg.prevSiblings.takeWhile(e => e.is[PsiWhiteSpace, PsiComment] || e.textMatches(",") || e.textMatches("[")).lastOption
        val range = opening.map(e => new TextRange(e.startOffset, firstExcessiveArg.getTextOffset + 1)).getOrElse(firstExcessiveArg.getTextRange)
        holder.createErrorAnnotation(range, ScalaBundle.message("too.many.type.arguments.for.typeparamowner", signatureOf(typeParamOwner)))
      }

      implicit val tcp: TypePresentationContext = typeArgList
      for {
        // the zip will cut away missing or excessive arguments
        (arg, param) <- args zip params
        argTy <- arg.`type`()
      } {
        lazy val argTyText = argTy.presentableText
        for (upperBound <- param.upperBound.toOption if !argTy.conforms(upperBound)) {
          holder.createErrorAnnotation(arg, ScalaBundle.message("type.arg.does.not.conform.to.upper.bound", argTyText, upperBound.presentableText, param.name))
        }

        for (lowerBound <- param.lowerBound.toOption if !lowerBound.conforms(argTy)) {
          holder.createErrorAnnotation(arg, ScalaBundle.message("type.arg.does.not.conform.to.lower.bound", argTyText, lowerBound.presentableText, param.name))
        }
      }
    }
  }

  private def signatureOf(typeParamOwner: PsiNamedElement with ScTypeParametersOwner): String = {
    val name = typeParamOwner.name
    val params = typeParamOwner.typeParameters.map(_.name).mkString(", ")
    s"$name[$params]"
  }
}
