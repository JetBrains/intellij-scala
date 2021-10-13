package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiComment, PsiWhiteSpace}
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.KindProjectorUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScTypeArgs, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.traceLogger.TraceLogger
import org.jetbrains.plugins.scala.lang.psi.types.{ScExistentialArgument, ScExistentialType, ScType, TypePresentationContext, extractTypeParameters}

object ScParameterizedTypeElementAnnotator extends ElementAnnotator[ScParameterizedTypeElement] {

  override def annotate(
    element:   ScParameterizedTypeElement,
    typeAware: Boolean
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = {
    val prefixType = element.typeElement.getTypeNoConstructor.toOption

    val projSubstitutor = element.typeElement.`type`().toOption match {
      case Some(p @ ScProjectionType(proj, _))                       => ScSubstitutor(proj).followed(p.actualSubst)
      case Some(ParameterizedType(p @ ScProjectionType(proj, _), _)) => ScSubstitutor(proj).followed(p.actualSubst)
      case _                                                         => ScSubstitutor.empty
    }

    val teText = element.typeElement.getText
    val isKindProjectorLambda =
      teText == KindProjectorUtil.Lambda ||
        teText == KindProjectorUtil.LambdaSymbolic ||
        KindProjectorUtil.syntaxIdsFor(element).contains(teText)

    prefixType.foreach { tpe =>
      val typeParams = extractTypeParameters(tpe)

      if (!isKindProjectorLambda) {
        annotateTypeArgs(
          typeParams,
          element.typeArgList,
          projSubstitutor,
          tpe.presentableText(element)
        )
      }
    }
  }

  def annotateTypeArgs(
    params:                 Seq[TypeParameter],
    typeArgList:            ScTypeArgs,
    contextSubstitutor:     ScSubstitutor,
    renderedTypeParamOwner: String
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = {
    implicit val tcp: TypePresentationContext = typeArgList

    val args = typeArgList.typeArgs

    if (args.length < params.length) {
      // Annotate missing arguments
      val missing     = params.drop(args.length)
      val missingText = missing.map(_.name).mkString(", ")

      val range =
        args.lastOption
            .map(e => new TextRange(e.endOffset - 1, typeArgList.endOffset))
            .getOrElse(typeArgList.getTextRange)

      holder.createErrorAnnotation(range, ScalaBundle.message("unspecified.type.parameters", missingText))
    } else if (args.length > params.length) {
      // Annotate to many arguments
      if (params.isEmpty) {
        holder.createErrorAnnotation(
          typeArgList,
          ScalaBundle.message("name.does.not.take.type.arguments", renderedTypeParamOwner)
        )
      } else {
        val firstExcessiveArg = args(params.length)

        val opening =
          firstExcessiveArg
            .prevSiblings
            .takeWhile(e => e.is[PsiWhiteSpace, PsiComment] || e.textMatches(",") || e.textMatches("["))
            .lastOption

        val range =
          opening
            .map(e => new TextRange(e.startOffset, firstExcessiveArg.getTextOffset + 1))
            .getOrElse(firstExcessiveArg.getTextRange)

        holder.createErrorAnnotation(
          range,
          ScalaBundle.message(
            "too.many.type.arguments.for.typeparamowner",
            renderedTypeParamOwner,
            params.length,
            args.length
          )
        )
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

  private def checkBounds(
    arg:        ScTypeElement,
    argTy:      ScType,
    param:      TypeParameter,
    substitute: ScSubstitutor
  )(implicit
    holder: ScalaAnnotationHolder,
    tcp:    TypePresentationContext
  ): Unit = TraceLogger.func {
    lazy val argTyText = argTy.presentableText
    val upper = param.upperType
    val lower = param.lowerType

    if (!argTy.conforms(substitute(upper))) {
      holder
        .createErrorAnnotation(
          arg,
          ScalaBundle.message("type.arg.does.not.conform.to.upper.bound", argTyText, upper.presentableText, param.name),
          ReportHighlightingErrorQuickFix
        )
    }

    if (!substitute(lower).conforms(argTy)) {
      holder
        .createErrorAnnotation(
          arg,
          ScalaBundle.message("type.arg.does.not.conform.to.lower.bound", argTyText, lower.presentableText, param.name),
          ReportHighlightingErrorQuickFix
        )
    }
  }

  private def checkHigherKindedType(
    arg:        ScTypeElement,
    argTy:      ScType,
    param:      TypeParameter,
    substitute: ScSubstitutor
  )(implicit
    holder: ScalaAnnotationHolder,
    tcp:    TypePresentationContext
  ): Unit = {
    val paramTyParams = param.typeParameters

    if (paramTyParams.nonEmpty) {
      val expectedTyConstr = (param.name, paramTyParams)
      argTy match {
        case TypeParameters(argParams) if argParams.nonEmpty =>
          val actualTyConstr = (argTy.presentableText, argParams)
          val actualDiff = TypeConstructorDiff.forActual(expectedTyConstr, actualTyConstr, substitute)
          if (actualDiff.exists(_.hasError)) {
            val expectedDiff = TypeConstructorDiff.forExpected(expectedTyConstr, actualTyConstr, substitute)
            holder.newAnnotation(HighlightSeverity.ERROR, ScalaBundle.message("type.constructor.does.not.conform", argTy.presentableText, expectedDiff.flatten.map(_.text).mkString))
              .range(arg)
              .tooltip(tooltipForDiffTrees(ScalaBundle.message("type.constructor.mismatch"), expectedDiff, actualDiff))
              .withFix(ReportHighlightingErrorQuickFix)
              .create()
          }
        case ty if ty.isNothing || ty.isAny => // nothing and any are ok
        case _ =>
          val actualTyConstr = (argTy.presentableText, Seq.empty)
          val expectedDiff = TypeConstructorDiff.forExpected(expectedTyConstr, actualTyConstr, substitute)
          val actualDiff = TypeConstructorDiff.forActual(expectedTyConstr, actualTyConstr, substitute)

          val paramText  = param.psiTypeParameter.asInstanceOf[ScTypeParam].typeParameterText
          holder.newAnnotation(HighlightSeverity.ERROR, ScalaBundle.message("expected.type.constructor", paramText))
            .range(arg)
            .tooltip(tooltipForDiffTrees(ScalaBundle.message("expected.type.constructor", ""), expectedDiff, actualDiff))
            .withFix(ReportHighlightingErrorQuickFix)
            .create()
      }
    }
  }

  private object TypeParameters {
    import org.jetbrains.plugins.scala.lang.psi.types.extractTypeParameters
    def unapply(ty: ScType): Option[Seq[TypeParameter]] =
      Option(extractTypeParameters(ty))
  }
}
