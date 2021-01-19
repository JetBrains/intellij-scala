package org.jetbrains.plugins.scala.lang.psi.impl.base.literals.escapers

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.escapers.ScalaStringParser.Log

import java.lang.{StringBuilder => JStringBuilder}
import java.util

/**
 * Initially based on [[com.intellij.codeInsight.CodeInsightUtilCore.StringParser]]
 * and [[com.intellij.codeInsight.CodeInsightUtilCore.parseStringCharacters]]
 *
 * Removed everything unrelated:
 *  - drop support for octal literals<br>
 *    (cause it's dropped in 2.13 https://github.com/scala/scala/pull/6324 and were deprecated for a long time)
 *  - remove logic with "isAfterEscapedBackslash"<br>
 *    (since 2.13.2 unicode escapes are not inlined in source code: https://github.com/scala/scala/pull/8282)
 *
 * @param isRaw includes
 *              - single line raw literal: raw"42"
 *              - multiline raw literal: raw"""42"""
 *              - multiline non-interpolated literal: """42"""
 * @see [[org.jetbrains.plugins.scala.highlighter.lexer.ScalaStringLiteralLexer]] and other lexers in same package
 */
final class ScalaStringParser(
  @Nullable
  sourceOffsets: Array[Int],
  isRaw: Boolean,
  exitOnEscapingWrongSymbol: Boolean = true
) {

  private val onlyUnicode: Boolean = isRaw

  def parse(chars: String, outChars: JStringBuilder): Boolean = {
    Log.assertTrue(sourceOffsets == null || sourceOffsets.length == chars.length() + 1);

    val hasAnyEscapeCandidates = chars.indexOf('\\') >= 0
    if (!hasAnyEscapeCandidates) {
      outChars.append(chars)
      if (sourceOffsets != null)
        util.Arrays.setAll(sourceOffsets, (i: Int) => i)
      return true
    }

    var index = 0
    val outOffset = outChars.length
    while (index < chars.length) {
      val c = chars.charAt(index)
      index += 1;

      if (sourceOffsets != null) {
        sourceOffsets(outChars.length - outOffset) = index - 1
        sourceOffsets(outChars.length + 1 - outOffset) = index
      }

      if (c != '\\')
        outChars.append(c)
      else {
        val res = parseEscapedSymbol(chars, outChars, index, outOffset)
        if (res == -1) {
          if (exitOnEscapingWrongSymbol)
            return false
          else if (index < chars.length)
            index += 1 // skip wrong escaped char: "a\j\ b" -> "ab"
        } else
          index = res

        if (sourceOffsets != null)
          sourceOffsets(outChars.length - outOffset) = index
      }
    }

    true
  }

  private def parseEscapedSymbol(chars: String, outChars: JStringBuilder, index0: Int, outOffset: Int): Int = {
    var index = index0
    if (index == chars.length) {
      if (isRaw) {
        outChars.append("\\")
        return index
      }
      else return -1
    }

    val c = chars.charAt(index)
    index += 1

    val newIndex =
      if (!onlyUnicode && ScalaStringParser.parseEscapedChar(c, outChars))
        index
      else if (c == 'u')
        parseUnicodeEscape(chars, outChars, index - 1)
      else -1

    if (newIndex == -1 & isRaw) {
      // just add whatever escape-looking char sequence
      outChars.append('\\')
      if (sourceOffsets != null)
        sourceOffsets(outChars.length - outOffset) = index - 1
      outChars.append(c)
      index
    }
    else newIndex
  }

  private def parseUnicodeEscape(s: String, outChars: JStringBuilder, index0: Int): Int = {
    var index = index0
    val len = s.length

    // uuuuu1234 is valid too
    do index += 1
    while (index < len && s.charAt(index) == 'u')

    if (index + 4 > len)
      return -1

    try {
      val hex = s.substring(index, index + 4)
      val code = Integer.parseInt(hex, 16)
      outChars.append(code.toChar)
      index + 4
    } catch {
      case _: NumberFormatException =>
        -1
    }
  }
}

object ScalaStringParser {

  private val Log = Logger.getInstance(this.getClass)

  private def parseEscapedChar(c: Char, outChars: JStringBuilder): Boolean =
    c match {
      case 'b'  =>
        outChars.append('\b')
        true
      case 't'  =>
        outChars.append('\t')
        true
      case 'n'  =>
        outChars.append('\n')
        true
      case 'f'  =>
        outChars.append('\f')
        true
      case 'r'  =>
        outChars.append('\r')
        true
      //case 's'  => // supported since Java 15, but not in Scala
      case '\\' =>
        outChars.append('\\')
        true
      case '\"' =>
        outChars.append('\"')
        true
      case '\n' =>
        //don't append anything
        true
      case _ =>
        false
    }
}