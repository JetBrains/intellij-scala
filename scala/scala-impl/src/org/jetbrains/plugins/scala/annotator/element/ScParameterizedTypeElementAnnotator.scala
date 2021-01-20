package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiComment, PsiNamedElement, PsiWhiteSpace}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.DesignatorOwner
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}

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
        if (typeParamOwner.typeParametersClause.isEmpty) {
          holder.createErrorAnnotation(typeArgList, ScalaBundle.message("name.does.not.take.type.arguments", typeParamOwner.name))
        } else {
          val firstExcessiveArg = args(params.length)
          val opening = firstExcessiveArg.prevSiblings.takeWhile(e => e.is[PsiWhiteSpace, PsiComment] || e.textMatches(",") || e.textMatches("[")).lastOption
          val range = opening.map(e => new TextRange(e.startOffset, firstExcessiveArg.getTextOffset + 1)).getOrElse(firstExcessiveArg.getTextRange)
          holder.createErrorAnnotation(range, ScalaBundle.message("too.many.type.arguments.for.typeparamowner", signatureOf(typeParamOwner)))
        }
      }

      implicit val tcp: TypePresentationContext = typeArgList
      for {
        // the zip will cut away missing or excessive arguments
        (arg, param) <- args zip params
        argTy <- arg.`type`()
      } {
        checkBounds(arg, argTy, param)
        checkHigherKindedType(arg, argTy, param)
      }
    }
  }

  private def checkBounds(arg: ScTypeElement, argTy: ScType, param: ScTypeParam)
                         (implicit holder: ScalaAnnotationHolder, tcp: TypePresentationContext): Unit = {
    lazy val argTyText = argTy.presentableText
    for (upperBound <- param.upperBound.toOption if !argTy.conforms(upperBound)) {
      holder.createErrorAnnotation(arg, ScalaBundle.message("type.arg.does.not.conform.to.upper.bound", argTyText, upperBound.presentableText, param.name))
        .registerFix(ReportHighlightingErrorQuickFix)
    }

    for (lowerBound <- param.lowerBound.toOption if !lowerBound.conforms(argTy)) {
      holder.createErrorAnnotation(arg, ScalaBundle.message("type.arg.does.not.conform.to.lower.bound", argTyText, lowerBound.presentableText, param.name))
        .registerFix(ReportHighlightingErrorQuickFix)
    }
  }

  private def checkHigherKindedType(arg: ScTypeElement, argTy: ScType, param: ScTypeParam)
                                   (implicit holder: ScalaAnnotationHolder, tcp: TypePresentationContext): Unit = {
    val paramTyParams = param.typeParameters.map(TypeParameter(_))

    if (paramTyParams.nonEmpty) {
      val expectedTyConstr = (param.name, paramTyParams)
      argTy.asOptionOf[DesignatorOwner].flatMap(_.polyTypeOption) match {
        case Some(ScTypePolymorphicType(_, argParams)) =>
          val actualTyConstr = (argTy.presentableText, argParams)
          val actualDiff = TypeConstructorDiff.forActual(expectedTyConstr, actualTyConstr)
          if (actualDiff.exists(_.isMismatch)) {
            val expectedDiff = TypeConstructorDiff.forExpected(expectedTyConstr, actualTyConstr)
            val annotation = holder.createErrorAnnotation(arg, ScalaBundle.message("type.constructor.does.not.conform", argTy.presentableText, expectedDiff.flatten.map(_.text).mkString))
            annotation.setTooltip(tooltipForDiffTrees(ScalaBundle.message("type.constructor.mismatch"), expectedDiff, actualDiff))
            annotation.registerFix(ReportHighlightingErrorQuickFix)
          }
        case _ =>
          val actualTyConstr = (argTy.presentableText, Seq.empty)
          val expectedDiff = TypeConstructorDiff.forExpected(expectedTyConstr, actualTyConstr)
          val actualDiff = TypeConstructorDiff.forActual(expectedTyConstr, actualTyConstr)

          val annotation = holder.createErrorAnnotation(arg, ScalaBundle.message("expected.type.constructor", param.typeParameterText))
          annotation.registerFix(ReportHighlightingErrorQuickFix)
          annotation.setTooltip(tooltipForDiffTrees(ScalaBundle.message("expected.type.constructor", ""), expectedDiff, actualDiff))
      }
    }
  }

  private def tooltipForDiffTrees(@Nls msg: String, expectedDiff: Tree[TypeConstructorDiff], actualDiff: Tree[TypeConstructorDiff]): String =
    annotator.tooltipForDiffTrees(msg, expectedDiff, actualDiff)(_.isMismatch, _.text)

  private def signatureOf(typeParamOwner: PsiNamedElement with ScTypeParametersOwner): String = {
    val name = typeParamOwner.name
    val params = typeParamOwner.typeParameters.map(_.name).mkString(", ")
    s"$name[$params]"
  }
}
