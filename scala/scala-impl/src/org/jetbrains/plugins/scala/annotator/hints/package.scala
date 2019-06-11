package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.editor.markup.TextAttributes

package object hints {
  implicit class TextAttributesExt(val v: TextAttributes) extends AnyVal {
    def + (attributes: TextAttributes): TextAttributes = {
      val result = v.clone()
      Option(attributes.getForegroundColor).foreach(result.setForegroundColor)
      Option(attributes.getBackgroundColor).foreach(result.setBackgroundColor)
      Option(attributes.getFontType).foreach(result.setFontType)
      Option(attributes.getEffectType).foreach(result.setEffectType)
      Option(attributes.getEffectColor).foreach(result.setEffectColor)
      result
    }

    def ++ (attributes: Iterable[TextAttributes]): TextAttributes =
      attributes.foldLeft(v)(_ + _)
  }
}
