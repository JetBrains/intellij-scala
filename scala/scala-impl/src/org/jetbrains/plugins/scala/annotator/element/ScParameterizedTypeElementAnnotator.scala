package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiComment, PsiNamedElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScTypeArgs, ScTypeElement, ScTypeVariableTypeElement, ScWildcardTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScExistentialArgument, ScExistentialType, ScType, TypePresentationContext}

object ScParameterizedTypeElementAnnotator extends ElementAnnotator[ScParameterizedTypeElement] {

  override def annotate(element: ScParameterizedTypeElement, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val typeParamOwner = element.typeElement.getTypeNoConstructor.toOption
      .flatMap(_.extractDesignated(expandAliases = false))
      .collect { case t: PsiNamedElement with ScTypeParametersOwner => t }
    
    val projSubstitutor = element.typeElement.`type`().toOption match {
      case Some(p@ScProjectionType(proj, _)) => ScSubstitutor(proj).followed(p.actualSubst)
      case Some(ParameterizedType(p@ScProjectionType(proj, _), _)) => ScSubstitutor(proj).followed(p.actualSubst)
      case _ => ScSubstitutor.empty
    }

    typeParamOwner.foreach(annotateTypeArgs(_, element.typeArgList, projSubstitutor))
  }

  def annotateTypeArgs(typeParamOwner: PsiNamedElement with ScTypeParametersOwner,
                       typeArgList: ScTypeArgs,
                       contextSubstitutor: ScSubstitutor)
                      (implicit holder: ScalaAnnotationHolder): Unit = {

    val params = typeParamOwner.typeParameters
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
    val substitute: ScSubstitutor = {
      val (ps, as) = (
        for {
          (param, arg) <- params zip args
          argTy <- arg.`type`().toOption
        } yield (param, argTy)
      ).unzip
      contextSubstitutor followed ScSubstitutor.bind(ps, as)
    }

    implicit val tcp: TypePresentationContext = typeArgList
    for {
      // the zip will cut away missing or excessive arguments
      (arg, param) <- args zip params
      argTy <- arg.`type`().toOption
      if !argTy.is[ScExistentialArgument, ScExistentialType]
    } {
      checkBounds(arg, argTy, param, substitute)
      checkHigherKindedType(arg, argTy, param, substitute)
    }
  }

  private def checkBounds(arg: ScTypeElement, argTy: ScType, param: ScTypeParam, substitute: ScSubstitutor)
                         (implicit holder: ScalaAnnotationHolder, tcp: TypePresentationContext): Unit = {
    lazy val argTyText = argTy.presentableText
    for (upperBound <- param.upperBound.toOption if !argTy.conforms(substitute(upperBound))) {
      holder.createErrorAnnotation(arg, ScalaBundle.message("type.arg.does.not.conform.to.upper.bound", argTyText, upperBound.presentableText, param.name))
        .registerFix(ReportHighlightingErrorQuickFix)
    }

    for (lowerBound <- param.lowerBound.toOption if !substitute(lowerBound).conforms(argTy)) {
      holder.createErrorAnnotation(arg, ScalaBundle.message("type.arg.does.not.conform.to.lower.bound", argTyText, lowerBound.presentableText, param.name))
        .registerFix(ReportHighlightingErrorQuickFix)
    }
  }

  private def checkHigherKindedType(arg: ScTypeElement, argTy: ScType, param: ScTypeParam, substitute: ScSubstitutor)
                                   (implicit holder: ScalaAnnotationHolder, tcp: TypePresentationContext): Unit = {
    val paramTyParams = param.typeParameters.map(TypeParameter(_))

    if (paramTyParams.nonEmpty) {
      val expectedTyConstr = (param.name, paramTyParams)
      argTy match {
        case TypeParameters(argParams) if argParams.nonEmpty =>
          val actualTyConstr = (argTy.presentableText, argParams)
          val actualDiff = TypeConstructorDiff.forActual(expectedTyConstr, actualTyConstr, substitute)
          if (actualDiff.exists(_.hasError)) {
            val expectedDiff = TypeConstructorDiff.forExpected(expectedTyConstr, actualTyConstr, substitute)
            val annotation = holder.createErrorAnnotation(arg, ScalaBundle.message("type.constructor.does.not.conform", argTy.presentableText, expectedDiff.flatten.map(_.text).mkString))
            annotation.setTooltip(tooltipForDiffTrees(ScalaBundle.message("type.constructor.mismatch"), expectedDiff, actualDiff))
            annotation.registerFix(ReportHighlightingErrorQuickFix)
          }
        case ty if ty.isNothing || ty.isAny => // nothing and any are ok
        case _ =>
          val actualTyConstr = (argTy.presentableText, Seq.empty)
          val expectedDiff = TypeConstructorDiff.forExpected(expectedTyConstr, actualTyConstr, substitute)
          val actualDiff = TypeConstructorDiff.forActual(expectedTyConstr, actualTyConstr, substitute)

          val annotation = holder.createErrorAnnotation(arg, ScalaBundle.message("expected.type.constructor", param.typeParameterText))
          annotation.registerFix(ReportHighlightingErrorQuickFix)
          annotation.setTooltip(tooltipForDiffTrees(ScalaBundle.message("expected.type.constructor", ""), expectedDiff, actualDiff))
      }
    }
  }

  private def signatureOf(typeParamOwner: PsiNamedElement with ScTypeParametersOwner): String = {
    val name = typeParamOwner.name
    val params = typeParamOwner.typeParameters.map(_.name).mkString(", ")
    s"$name[$params]"
  }

  private object TypeParameters {
    def unapply(ty: ScType): Option[Seq[TypeParameter]] = ty match {
      case designatorOwner: DesignatorOwner =>
        designatorOwner.polyTypeOption.map(_.typeParameters)
      case typeParameter: TypeParameterType =>
        Some(typeParameter.typeParameters)
      case _ =>
        None
    }
  }
}
