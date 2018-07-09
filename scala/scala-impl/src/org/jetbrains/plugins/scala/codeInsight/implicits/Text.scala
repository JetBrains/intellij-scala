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
    val result1 = this.attributes.map(attributes + _).getOrElse(attributes)

    if (hyperlink) {
      val result2 = result1 + editor.getColorsScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)
      result2.setEffectType(EffectType.LINE_UNDERSCORE)
      result2
    } else  {
      result1
    }
  }
}
