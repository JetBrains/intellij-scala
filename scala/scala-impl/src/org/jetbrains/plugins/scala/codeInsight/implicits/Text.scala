package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.{CodeInsightColors, EditorColors}
import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import com.intellij.pom.Navigatable

private class Text(val string: String,
                   val attributes: Option[TextAttributes],
                   val tooltip: Option[String],
                   val navigatable: Option[Navigatable],
                   val error: Boolean,
                   val expansion: Option[() => Seq[Text]]) {

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
    new Text(string, Some(this.attributes.map(_ + attributes).getOrElse(attributes)), tooltip, navigatable, error, expansion)
}

private object Text {
  def apply(string: String,
            attributes: Option[TextAttributes] = None,
            tooltip: Option[String] = None,
            navigatable: Option[Navigatable] = None,
            error: Boolean = false,
            expansion: Option[() => Seq[Text]] = None): Text =
    new Text(string, attributes, tooltip, navigatable, error, expansion)
}