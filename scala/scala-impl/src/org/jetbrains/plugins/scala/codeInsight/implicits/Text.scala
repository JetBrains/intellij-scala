package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import com.intellij.pom.Navigatable

private case class Text(string: String,
                        attributes: Option[TextAttributes] = None,
                        navigatable: Option[Navigatable] = None,
                        expansion: Option[() => Seq[Text]] = None) {

  var hyperlink: Boolean = false

  def effective(editor: Editor, attributes: TextAttributes): TextAttributes = {
    val result = attributes.clone()
    if (navigatable.nonEmpty && hyperlink) {
      val linkAttributes = editor.getColorsScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)
      result.setForegroundColor(linkAttributes.getForegroundColor)
      result.setEffectType(EffectType.LINE_UNDERSCORE)
      result.setEffectColor(linkAttributes.getForegroundColor)
    } else {
      this.attributes.foreach { it =>
        result.setEffectType(it.getEffectType)
        result.setEffectColor(it.getEffectColor)
      }
    }
    result
  }
}
