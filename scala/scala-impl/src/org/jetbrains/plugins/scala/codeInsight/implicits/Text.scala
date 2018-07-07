package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import com.intellij.pom.Navigatable

private case class Text(string: String,
                        attributes: Option[TextAttributes] = None,
                        tooltip: Option[String] = None,
                        navigatable: Option[Navigatable] = None,
                        expansion: Option[() => Seq[Text]] = None) {

  var hyperlink: Boolean = false

  def effective(editor: Editor, attributes: TextAttributes): TextAttributes = {
    val result = attributes.clone()

    this.attributes.foreach(copyAttributes(_, result))

    if (hyperlink) {
      val linkAttributes = editor.getColorsScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)
      copyAttributes(linkAttributes, result)
      result.setEffectType(EffectType.LINE_UNDERSCORE)
    }

    result
  }

  private def copyAttributes(source: TextAttributes, destination: TextAttributes): Unit = {
    Option(source.getForegroundColor).foreach(destination.setForegroundColor)
    Option(source.getBackgroundColor).foreach(destination.setBackgroundColor)
    Option(source.getEffectType).foreach(destination.setEffectType)
    Option(source.getEffectColor).foreach(destination.setEffectColor)
  }
}
