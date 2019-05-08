package org.jetbrains.plugins.scala
package format

import com.intellij.openapi.util.text.StringUtil

/**
 * Pavel Fatin
 */
object StringConcatenationFormatter extends StringFormatter {
  override def format(parts: Seq[StringPart]): String = {
    if (parts.isEmpty) quoted("") else {
      val strings = parts.collect {
        case Text(s) => quoted(StringUtil.escapeStringCharacters(s))
        case injection: Injection =>
          val s = if (injection.isLiteral || injection.isComplexBlock) injection.text else injection.value
          if (injection.isFormattingRequired)
            "%s.formatted(%s)".format(s, quoted(injection.format))
          else
            s
      }
      strings.mkString(" + ")
    }
  }

  private def quoted(s: String) = '"' + s + '"'
}
