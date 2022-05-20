package org.jetbrains.plugins.scala.format

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.text.CharSequenceSubSequence
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.escapers.ScalaStringParser
import org.jetbrains.plugins.scala.util.MultilineStringUtil.MultilineQuotes

import java.lang

private object ScalaStringUtils {

  def unescapeStringCharacters(content: String, isRaw: Boolean): String = {
    val parser = new ScalaStringParser(null, isRaw, exitOnEscapingWrongSymbol = false)
    val builder = new java.lang.StringBuilder()
    parser.parse(content, builder)
    builder.toString
  }

  // just run the tests...
  def escapePlainText(s0: String, toMultiline: Boolean, prefix: String): String = {
    val escapeDollar = prefix.nonEmpty
    val isRawContent = prefix == "raw" || prefix.isEmpty && toMultiline

    val s1 = if (escapeDollar) s0.replaceAll("\\$", "\\$\\$") else s0

    val s2 = if (toMultiline) s1.replace("\r", "") else s1

    val s3 = if (isRawContent) {
      escapeForRawContent(s2, toMultiline)
    }
    else {
      val additionalEscape = if (toMultiline) "" else "\"" // we can use single '"' inside multiline strings
      escapeStringCharacters(s2, additionalEscape, escapeNewLine = !toMultiline)
    }

    val tripleQuotes = tripleQuotesFor(isRawContent, toMultiline)
    val s4 = s3.replace(MultilineQuotes, tripleQuotes)
    s4
  }

  // detects \u0025, \uu0023
  private val UnicodeEscapeRegex = "\\\\u+\\d\\d\\d\\d".r

  /**
   * just run the tests...
   *
   * It uses several workarounds:
   *  1. Fixes raw content (which doesn't actually have any escapes) which contains candiates for unicode escapes: {{{
   *     "text \\u0025"   -> """text \u005cu0025"""
   *     "text \\\\u0025" -> """text \\u0025"""
   *     "text \\ \t \\"  -> """text \ 	 \"""  }}}
   *     NOTE: <br>
   *     When converting "\\u0025" to multiline we should get """\u005cu0025""" (\u005c == '\\')<br>
   *     This is so because in raw strings you can't use """\u0025""", cause it's treated as a unicode sequence.<br>
   *     You also can't use """\\u0025""" cause it equals to "\\\\u0025"
   *  1. In case of non-multiline raw"string", fix quotes: {{{
   *     raw"""a " b"""   -> raw"a \u0022 b"
   *     raw"""a \" b"""  -> raw"aaa \u005c\u0022 b"   }}}
   *     NOTE:<br>
   *     This hack is required due to \" is not recognised inside strings (raw"\"")<br>
   *     (see [[https://github.com/scala/bug/issues/6476]])
   *
   *
   * TODO: handle Scala 3 case, where raw strings don't support Unicode escapes
   */
  private def escapeForRawContent(content: String, toMultiline: Boolean): String = {
    val buffer = new lang.StringBuilder(content.length)

    var idx = 0

    while (idx < content.length) {
      val ch = content.charAt(idx)
      ch match {
        case '\\' if idx + 1 < content.length =>
          val chNext = content.charAt(idx + 1)
          chNext match {
            case 'u' =>
              UnicodeEscapeRegex.findPrefixMatchOf(new CharSequenceSubSequence(content, idx, content.length)) match {
                case Some(mat) =>
                  val matchedCode = mat.matched
                  buffer.append("\\u005c").append(matchedCode.drop(1))
                  idx += matchedCode.length
                case _ =>
                  buffer.append("\\u")
                  idx += 2
              }
            case '"' if !toMultiline  =>
              buffer.append("\\u005c\\u0022")
              idx += 2
            case _ =>
              buffer.append('\\').append(chNext)
              idx += 2
          }
        case '"' if !toMultiline =>
          buffer.append("\\u0022")
          idx += 1
        case _ =>
          buffer.append(ch)
          idx += 1
      }
    }

    buffer.toString
  }

  private def tripleQuotesFor(isRaw: Boolean, toMultiline: Boolean): String =
    (toMultiline, isRaw) match {
      // s"\"\"\""
      case (false, false) => "\\\"\\\"\\\""
      //raw"\u0022\u0022\u0022"
      case (false, true)  => "\\u0022\\u0022\\u0022"
      // s"""""\""""
      case (true, false)  => "\"\"\\\""
      // """""\u0022"""
      case (true, true)   => "\"\"\\u0022"
    }

  def escapeStringCharacters(s: String, additionalChars: String, escapeNewLine: Boolean): String = {
    val buffer = new java.lang.StringBuilder(s.length)
    escapeStringCharacters(
      s.length,
      s,
      additionalChars,
      escapeSlash = true,
      escapeUnicode = true,
      escapeNewLine = escapeNewLine,
      buffer
    )
    buffer.toString
  }

  /**
   * copy of [[com.intellij.openapi.util.text.StringUtil]]<br>
   * added `escapeNewLine` parameter
   */
  //noinspection SameParameterValue
  private def escapeStringCharacters(
    length: Int,
    str: String,
    additionalChars: String,
    escapeSlash: Boolean,
    escapeUnicode: Boolean,
    escapeNewLine: Boolean,
    buffer: java.lang.StringBuilder
  ): java.lang.StringBuilder = {
    var prev = 0
    var idx = 0

    while (idx < length) {
      val ch = str.charAt(idx)
      ch match {
        case '\b'                  => buffer.append("\\b")
        case '\t'                  => buffer.append("\\t")
        case '\f'                  => buffer.append("\\f")
        case '\r'                  => buffer.append("\\r")
        case '\n' if escapeNewLine => buffer.append("\\n")
        case '\n'                  => buffer.append("\n")
        case _ =>
          if (escapeSlash && ch == '\\')
            buffer.append("\\\\")
          else if (additionalChars != null && additionalChars.indexOf(ch) > -1 && (escapeSlash || prev != '\\'))
            buffer.append("\\").append(ch)
          else if (escapeUnicode && !StringUtil.isPrintableUnicode(ch)) {
            val hexCode = StringUtil.toUpperCase(Integer.toHexString(ch))
            buffer.append("\\u")
            var paddingCount = 4 - hexCode.length
            while ( {paddingCount -= 1; paddingCount + 1> 0})
              buffer.append(0)
            buffer.append(hexCode)
          }
          else buffer.append(ch)
      }

      prev = ch
      idx += 1
    }

    buffer
  }

}
