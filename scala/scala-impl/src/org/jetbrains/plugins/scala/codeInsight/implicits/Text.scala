package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import com.intellij.pom.Navigatable

private case class Text(string: String,
                        attributes: Option[TextAttributes] = None,
                        tooltip: Option[String] = None,
                        navigatable: Option[Navigatable] = None,
                        error: Boolean = false,
                        expansion: Option[() => Seq[Text]] = None) {

  var hyperlink: Boolean = false

  var highlighted: Boolean = false

  def effective(editor: Editor, attributes: TextAttributes): TextAttributes = {
    var result = attributes.clone()

    this.attributes.foreach(result += _)

    if (highlighted) {
      result.setForegroundColor(result.getForegroundColor.brighter)
    }

    if (hyperlink) {
      result += editor.getColorsScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)
      result.setEffectType(EffectType.LINE_UNDERSCORE)
    }

    result
  }
}
