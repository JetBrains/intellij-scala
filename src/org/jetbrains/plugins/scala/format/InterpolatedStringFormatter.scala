package org.jetbrains.plugins.scala
package format

import com.intellij.openapi.util.text.StringUtil

/**
 * Pavel Fatin
 */

object InterpolatedStringFormatter extends StringFormatter {
  def format(parts: Seq[StringPart]) = {
    val content = formatContent(parts)
    val prefix = {
      val formattingRequired = parts.exists {
        case it: Injection => it.isFormattingRequired
        case _ => false
      }
      if (formattingRequired) "f" else "s"
    }
    prefix + '"' + content + '"'
  }

  def formatContent(parts: Seq[StringPart]): String = {
    val strings = parts.collect {
      case Text(s) => StringUtil.escapeStringCharacters(StringUtil.escapeSlashes(s.replaceAll("\\$", "\\$\\$")))
      case it: Injection =>
        val text = it.value
        if (it.isLiteral && !it.isFormattingRequired) text else {
          val presentation = if (it.isComplexBlock ||
                  (!it.isLiteral && it.isAlphanumericIdentifier)) "$" + text else "${" + text + "}"
          if (it.isFormattingRequired) presentation + it.format else presentation
        }
    }
    strings.mkString
  }
}
