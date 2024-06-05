package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.annotator.{ScalaAnnotationHolder, TypeConstructorDiff, tooltipForDiffTrees}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.KindProjectorUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement, ScTypeVariableTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
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
      implicit val tpc: TypePresentationContext = element

      if (!isKindProjectorLambda) {
        annotateTypeArgs[ScTypeElement](
          typeParams,
          element.typeArgList.typeArgs,
          element.typeArgList.getTextRange,
          projSubstitutor,
          tpe.presentableText(element),
          _.`type`()
        )
      }
    }
  }

  def annotateTypeArgs[T <: PsiElement](
    params:                 Seq[TypeParameter],
    args:                   Seq[T],
    annotationRange:        TextRange,
    contextSubstitutor:     ScSubstitutor,
    renderedTypeParamOwner: String,
    getType:                T => TypeResult,
    isForContextBound:      Boolean = false
  )(implicit
    tpc:    TypePresentationContext,
    holder: ScalaAnnotationHolder
  ): Unit = {

    if (args.length < params.length) {
      // Annotate missing arguments
      val missing = params.drop(args.length)
      val missingText = missing.map(_.name).mkString(", ")

      val range =
        if (isForContextBound) annotationRange
        else
          args.lastOption
              .map(e => new TextRange(e.endOffset - 1, annotationRange.getEndOffset))
              .getOrElse(annotationRange)

      holder.createErrorAnnotation(range, ScalaBundle.message("unspecified.type.parameters", missingText))
    } else if (args.length > params.length) {
      // Annotate to many arguments
      if (params.isEmpty) {
        holder.createErrorAnnotation(
          annotationRange,
          ScalaBundle.message("name.does.not.take.type.arguments", renderedTypeParamOwner)
        )
      } else {
        val firstExcessiveArg = args(params.length)

        val opening =
          firstExcessiveArg.prevSiblings
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
          (param, arg) <- params.zip(args)
          argTy        <- getType(arg).toOption
        } yield (param, argTy)
      ).unzip
      contextSubstitutor.followed(ScSubstitutor.bind(ps, as))
    }

    def argIsDesignatedToTypeVariable(arg: T): Boolean = arg match {
      case _: ScTypeVariableTypeElement => true
      case ste: ScSimpleTypeElement =>
        ste.reference.flatMap(_.bind()).exists(_.element.is[ScTypeVariableTypeElement])
      case _ => false
    }

    for {
      // the zip will cut away missing or excessive arguments
      (arg, param) <- args.zip(params)
      argTy        <- getType(arg).toOption
      range        = if (isForContextBound) annotationRange else arg.getTextRange
      if !argTy.is[ScExistentialArgument, ScExistentialType] &&
        !argIsDesignatedToTypeVariable(arg) &&
        !KindProjectorUtil.syntaxIdsFor(arg).contains(arg.getText)
    } {
      checkBounds(range, argTy, param, substitute)
      checkHigherKindedType(range, argTy, param, substitute)
    }
  }

  private def checkBounds(
    range:                  TextRange,
    argTy:                  ScType,
    param:                  TypeParameter,
    substitute:             ScSubstitutor,
  )(implicit
    holder: ScalaAnnotationHolder,
    tpc:    TypePresentationContext
  ): Unit = {
    lazy val argTyText = argTy.presentableText

    def hasUnboundedWildcards(tpe: ScType): Boolean = {
      var res = false

      tpe.recursiveUpdate {
        case _: ScExistentialType => Stop
        case _: ScExistentialArgument => res = true; Stop
        case _ => ProcessSubtypes
      }

      res
    }

    val upper = {
      val substed = substitute(param.upperType).removeAbstracts

      if (hasUnboundedWildcards(substed)) ScExistentialType(substed)
      else                                substed
    }

    val lower = {
      val substed = substitute(param.lowerType).removeAbstracts

      if (hasUnboundedWildcards(substed)) ScExistentialType(substed)
      else                                substed
    }

    if (!argTy.conforms(upper)) {
      argTy match {
        case ParameterizedType(ScProjectionType(_, _: ScTypeAliasDefinition), _) => return // TODO Workaround, fix testSCL22257
        case _ =>
      }
      holder
        .createErrorAnnotation(
          range,
          ScalaBundle.message("type.arg.does.not.conform.to.upper.bound", argTyText, upper.presentableText, param.name),
          ReportHighlightingErrorQuickFix
        )
    }

    if (!lower.conforms(argTy)) {
      holder
        .createErrorAnnotation(
          range,
          ScalaBundle.message("type.arg.does.not.conform.to.lower.bound", argTyText, lower.presentableText, param.name),
          ReportHighlightingErrorQuickFix
        )
    }
  }

  private def checkHigherKindedType(
    range:      TextRange,
    argTy:      ScType,
    param:      TypeParameter,
    substitute: ScSubstitutor
  )(implicit
    holder: ScalaAnnotationHolder,
    tpc:    TypePresentationContext
  ): Unit = {
    val paramTyParams = param.typeParameters

    val expectedTyConstr = (param.name, paramTyParams)

    argTy match {
      case TypeParameters(argParams) if argParams.nonEmpty =>
        val actualTyConstr = (argTy.presentableText, argParams)
        val actualDiff     = TypeConstructorDiff.forActual(expectedTyConstr, actualTyConstr, substitute)

        if (actualDiff.exists(_.hasError)) {
          val expectedDiff = TypeConstructorDiff.forExpected(expectedTyConstr, actualTyConstr, substitute)
          holder
            .newAnnotation(
              HighlightSeverity.ERROR,
              ScalaBundle.message(
                "type.constructor.does.not.conform",
                argTy.presentableText,
                expectedDiff.flatten.map(_.text).mkString
              )
            )
            .range(range)
            .tooltip(tooltipForDiffTrees(ScalaBundle.message("type.constructor.mismatch"), expectedDiff, actualDiff))
            .withFix(ReportHighlightingErrorQuickFix)
            .create()
        }
      case ty if ty.isNothing || ty.isAny => // nothing and any are ok
      case _ if paramTyParams.nonEmpty=>
        val actualTyConstr = (argTy.presentableText, Seq.empty)
        val expectedDiff   = TypeConstructorDiff.forExpected(expectedTyConstr, actualTyConstr, substitute)
        val actualDiff     = TypeConstructorDiff.forActual(expectedTyConstr, actualTyConstr, substitute)
        val paramText      = param.psiTypeParameter.asInstanceOf[ScTypeParam].typeParameterText

        holder
          .newAnnotation(HighlightSeverity.ERROR, ScalaBundle.message("expected.type.constructor", paramText))
          .range(range)
          .tooltip(
            tooltipForDiffTrees(ScalaBundle.message("expected.type.constructor", ""), expectedDiff, actualDiff)
          )
          .withFix(ReportHighlightingErrorQuickFix)
          .create()
      case _ =>
    }
  }

  private object TypeParameters {
    import org.jetbrains.plugins.scala.lang.psi.types.extractTypeParameters
    def unapply(ty: ScType): Option[Seq[TypeParameter]] =
      Option(extractTypeParameters(ty))
  }
}
