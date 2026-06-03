package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.psi.util.PsiLiteralUtil
import org.jetbrains.annotations.Nullable

import java.lang.{Long => JLong}

package object literals {
  /*
    Scala supports upper case literal prefixes for hexadecimal and binary literals,
    but Java does not, so the PsiLiteralUtil methods do not handle them correctly.
    So we need to convert them to lower case before parsing.
   */
  private def lowercaseLiteralPrefix(text: String): String = {
    if (text.length >= 2) {
      text(1) match {
        case 'X' => text.patch(1, "x", 1)
        case 'B' => text.patch(1, "b", 1)
        case _ => text
      }
    } else text
  }

  /*
    Scala 3 doesn't have octal literals, but supports leading zeros in decimal literals.
    PsiLiteralUtil will parse octal numbers, so we need to strip the leading zeros to make them parse as decimal.
   */
  private def doStripLeading0(text: String, stripLeading0: => Boolean): String = {
    if (text.startsWith("0") && text.length >= 2 && text(1).isDigit && stripLeading0) {
      val withoutLeadingZeros = text.dropWhile(_ == '0')
      if (withoutLeadingZeros.isEmpty) "0"
      else withoutLeadingZeros
    } else {
      text
    }
  }

  private def prepareNumParsing(text: String, stripLeading0: => Boolean) =
    lowercaseLiteralPrefix(doStripLeading0(text, stripLeading0))

  @Nullable
  def parseInteger(text: String, stripLeading0: => Boolean): Integer = {
    PsiLiteralUtil.parseInteger(prepareNumParsing(text, stripLeading0))
  }

  @Nullable
  def parseLong(text: String, stripLeading0: => Boolean): JLong = {
    PsiLiteralUtil.parseLong(prepareNumParsing(text, stripLeading0))
  }
}
