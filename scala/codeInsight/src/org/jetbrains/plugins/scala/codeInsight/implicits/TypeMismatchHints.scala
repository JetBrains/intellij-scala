package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt.Insets

import com.intellij.openapi.editor.colors.{CodeInsightColors, EditorFontType}
import org.jetbrains.plugins.scala.annotator.TypeDiff.{Group, Match, Mismatch}
import org.jetbrains.plugins.scala.annotator.{TypeDiff, TypeMismatchError, TypeMismatchHighlightingMode}
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHintsPass.{foldedAttributes, foldedString}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScPostfixExpr}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

// TODO experimental feature (SCL-15250)
private trait TypeMismatchHints { self: ImplicitHintsPass =>
  protected def collectTypeMismatches() {
    val mode = TypeMismatchHighlightingMode.in(editor.getProject)

    if (mode != TypeMismatchHighlightingMode.HIGHLIGHT_EXPRESSION) {
      rootElement.elements.foreach { e =>
        TypeMismatchError(e).foreach { case TypeMismatchError(expectedType, actualType, message, _) =>
          val needsParentheses = e.isInstanceOf[ScInfixExpr] || e.isInstanceOf[ScPostfixExpr]
          if (needsParentheses) {
            hints +:= Hint(Seq(Text("(")), e, suffix = false)
          }
          val typeParts =
            if (mode == TypeMismatchHighlightingMode.SHOW_TYPE_MISMATCH_HINT) typeMismatchHintWith(message)
            else (expectedType, actualType).zipped.map(partsOf(_, _, message)).headOption.getOrElse(Seq(Text("")))
          val parts = Text(": ") +: typeParts |> { parts =>
            if (needsParentheses) Text(")") +: parts else parts
          }
          val spaceWidth = editor.getComponent.getFontMetrics(editor.getColorsScheme.getFont(EditorFontType.PLAIN)).charWidth(' ')
          hints +:= Hint(parts, e, margin = if (needsParentheses) None else Some(new Insets(0, spaceWidth, 0, 0)), suffix = true, relatesToPrecedingElement = true)
        }
      }
    }
  }

  private def partsOf(expected: ScType, actual: ScType, message: String): Seq[Text] = {
    def toText(diff: TypeDiff): Text = diff match {
      case Group(diffs) =>
        Text(foldedString,
          foldedAttributes(diff.flatten.exists(_.is[Mismatch]))(editor.getColorsScheme),
          expansion = Some(() => diffs.map(toText)))
      case Match(text, tpe) =>
        Text(text,
          tooltip = tpe.map(_.canonicalText.replaceFirst("_root_.", "")),
          navigatable = tpe.flatMap(_.extractClass))
      case Mismatch(text, tpe) =>
        Text(text,
          attributes = Some(editor.getColorsScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)),
          tooltip = tpe.map(_.canonicalText.replaceFirst("_root_.", "")),
          navigatable = tpe.flatMap(_.extractClass))
    }
    // TODO user-configurable maxChars
    TypeDiff.forActual(expected, actual)
      .flattenTo(maxChars = 25, groupLength = foldedString.length)
      .map(toText)
      .map(_.copy(errorTooltip = Some(message)))
  }

  private def typeMismatchHintWith(message: String): Seq[Text] =
    Seq(Text("<:",
      attributes = Some(editor.getColorsScheme.getAttributes(CodeInsightColors.MARKED_FOR_REMOVAL_ATTRIBUTES)),
      errorTooltip = Some(message)))
}
