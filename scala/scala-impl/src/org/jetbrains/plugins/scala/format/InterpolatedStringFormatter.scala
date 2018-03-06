package org.jetbrains.plugins.scala
package format

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.extensions.TraversableExt
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier

/**
 * Pavel Fatin
 */

object InterpolatedStringFormatter extends StringFormatter {
  def format(parts: Seq[StringPart]): String = {
    val toMultiline = parts.exists {
      case Text(s) => s.contains("\n")
      case i: Injection => i.value == "\n"
      case _ => false
    }
    format(parts, toMultiline)
  }

  def format(parts: Seq[StringPart], toMultiline: Boolean): String = {
    val prefix = {
      val injections = parts.filterBy[Injection]

      if (injections.forall(injectByValue)) ""
      else if (injections.exists(_.isFormattingRequired)) "f"
      else "s"
    }
    val content = formatContent(parts, toMultiline, needPrefix = prefix.nonEmpty)
    val quote = if (toMultiline) "\"\"\"" else "\""
    s"$prefix$quote$content$quote"
  }

  def formatContent(parts: Seq[StringPart], toMultiline: Boolean = false, needPrefix: Boolean = true): String = {
    val strings = parts.collect {
      case Text(s) => escapePlainText(s, toMultiline, escapeDollar = needPrefix)
      case it: Injection =>
        val text = it.value
        if (injectByValue(it)) text
        else {
          val presentation =
            if ((it.isComplexBlock || (!it.isLiteral && it.isAlphanumericIdentifier)) && noBraces(parts, it)) "$" + text else "${" + text + "}"
          if (it.isFormattingRequired) presentation + it.format else presentation
        }
    }
    strings.mkString
  }

  private def injectByValue(it: Injection) = it.isLiteral && !it.isFormattingRequired

  private def escapePlainText(s: String, toMultiline: Boolean, escapeDollar: Boolean): String = {
    val s1 = if (escapeDollar) s.replaceAll("\\$", "\\$\\$") else s

    if (toMultiline) s1.replace("\r", "")
    else StringUtil.escapeStringCharacters(s1)
  }

  def noBraces(parts: Seq[StringPart], it: Injection): Boolean =  {
    val ind = parts.indexOf(it)
    if (ind + 1 < parts.size) {
      parts(ind + 1) match {
        case Text(s) =>
          return s.isEmpty || !isIdentifier(it.text + s.charAt(0)) ||
                s.startsWith("`") || s.exists(_ == '$')
        case _ =>
      }
    }
    true
  }
}
