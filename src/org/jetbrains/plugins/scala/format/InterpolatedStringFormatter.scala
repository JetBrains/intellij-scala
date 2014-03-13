package org.jetbrains.plugins.scala
package format

import com.intellij.openapi.util.text.StringUtil

/**
 * Pavel Fatin
 */

object InterpolatedStringFormatter extends StringFormatter {
  def format(parts: Seq[StringPart]) = format(parts, toMultiline = false)

  def format(parts: Seq[StringPart], toMultiline: Boolean) = {
    val content = formatContent(parts, toMultiline)
    val prefix = {
      val formattingRequired = parts.exists {
        case it: Injection => it.isFormattingRequired
        case _ => false
      }
      if (formattingRequired) "f" else "s"
    }
    val quote = if (toMultiline) "\"\"\"" else "\""
    s"$prefix$quote$content$quote"
  }

  def formatContent(parts: Seq[StringPart], toMultiline: Boolean = false): String = {
    val strings = parts.collect {
      case Text(s) if toMultiline => s
      case Text(s) => StringUtil.escapeStringCharacters(s.replaceAll("\\$", "\\$\\$"))
      case it: Injection =>
        val text = it.value
        if (it.isLiteral && !it.isFormattingRequired) text else {
          val presentation =
            if ((it.isComplexBlock || (!it.isLiteral && it.isAlphanumericIdentifier)) && noBraces(parts, it)) "$" + text else "${" + text + "}"
          if (it.isFormattingRequired) presentation + it.format else presentation
        }
    }
    strings.mkString
  }

  def noBraces(parts: Seq[StringPart], it: Injection): Boolean =  {
    val ind = parts.indexOf(it)
    if (ind + 1 < parts.size) {
      parts(ind + 1) match {
        case Text(s) => return s.isEmpty || s.startsWith(" ")
        case _ =>  true
      }
    }
    true
  }
}
