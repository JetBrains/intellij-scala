package org.jetbrains.plugins.scala.annotator.hints

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.{CodeInsightColors, EditorColors}
import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.scala.extensions.ObjectExt

case class Text(string: String,
                attributes: Option[TextAttributes] = None,
                effectRange: Option[(Int, Int)]    = None,
                tooltip: () => Option[String]      = () => None,
                navigatable: Option[Navigatable]   = None,
                errorTooltip: Option[ErrorTooltip] = None,
                expansion: Option[() => Seq[Text]] = None) {

  var hyperlink: Boolean = false

  var highlighted: Boolean = false

  def effective(editor: Editor, attributes: TextAttributes): TextAttributes = {
    var result = attributes.clone()

    result ++= this.attributes

    if (highlighted) {
      result += editor.getColorsScheme.getAttributes(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES)
    }

    if (hyperlink) {
      result += editor.getColorsScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)
      result.setEffectType(EffectType.LINE_UNDERSCORE)
    }

    result
  }

  def withAttributes(attributes: TextAttributes): Text =
    copy(attributes = Some(this.attributes.map(_ + attributes).getOrElse(attributes)))

  def withErrorTooltip(tooltip: ErrorTooltip): Text =
    copy(errorTooltip = Some(tooltip))

  // We want auto-generate apply() and copy() methods, but reference-based equality
  override def equals(obj: scala.Any): Boolean = obj.asOptionOf[AnyRef].exists(eq)

  override def hashCode(): Int = System.identityHashCode(this)
}
