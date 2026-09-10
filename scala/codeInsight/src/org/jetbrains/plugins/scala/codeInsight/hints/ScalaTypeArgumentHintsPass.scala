package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.hints.Hint.HintPosition
import org.jetbrains.plugins.scala.annotator.hints.{Hint, Text}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings
import org.jetbrains.plugins.scala.extensions.IterableExt
import org.jetbrains.plugins.scala.lang.psi.api.InvocationDetails.{ArgumentClause, TypeArgument}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameterType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScAbstractType, TypePresentationContext}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

private[codeInsight] trait ScalaTypeArgumentHintsPass {
  protected def showTypeArgumentHints: Boolean =
    ScalaHintsSettings.xRayMode && ScalaApplicationSettings.XRAY_SHOW_TYPE_ARGUMENT_HINTS

  protected def typeArgumentHints(clause: ArgumentClause.Type, typeParamIds: Set[Long], editor: Editor): Seq[Hint] = {
    if (!showTypeArgumentHints) return Seq.empty

    def isInferred(argument: TypeArgument): Boolean = !argument.tpe.subtypeExists {
      case TypeParameterType(parameter) => typeParamIds.contains(parameter.typeParamId)
      case UndefinedType(parameter, _)  => typeParamIds.contains(parameter.typeParamId)
      case _: ScAbstractType            => true
      case _                            => false
    }

    def textOf(argument: TypeArgument, anchor: PsiElement): Seq[Text] =
      textPartsOf(
        argument.tpe,
        ScalaCodeInsightSettings.getInstance.presentationLength,
        anchor
      )(editor.getColorsScheme, TypePresentationContext(anchor), Context(anchor))

    val inferred = clause.arguments.filterNot(_.isExplicit)
    val presentation = clause.origin match {
      case None if inferred.nonEmpty && inferred.forall(isInferred) =>
        val parts = inferred.map(textOf(_, clause.anchor)).intersperse(Seq(Text(", "))).flatten
        Some(clause.anchor -> (Text("[") +: parts :+ Text("]")))
      case Some(args) if args.hasNamedTypeArgs =>
        val parts = inferred.filter(isInferred).map { argument =>
          Text(s"${argument.parameter.name} = ") +: textOf(argument, args)
        }.intersperse(Seq(Text(", "))).flatten
        args.typeArguments.lastOption.filter(_ => parts.nonEmpty).map(_ -> (Text(", ") +: parts))
      case _ => None
    }

    presentation.toSeq.map { case (anchor, parts) =>
      Hint(parts, anchor, position = HintPosition.AfterElement, relatesToPrecedingElement = true)
    }
  }
}
