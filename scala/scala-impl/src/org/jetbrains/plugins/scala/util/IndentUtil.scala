package org.jetbrains.plugins.scala.util

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, Whitespace}

object IndentUtil {

  def calcIndent(text: CharSequence, tabSize: Int): Int =
    calcIndent(text, 0, tabSize)

  def calcIndent(text: CharSequence, offset: Int, tabSize: Int): Int = {
    var result = 0

    var idx = offset
    while (idx < text.length) {
      val c = text.charAt(idx)
      if (!Character.isWhitespace(c)) return result
      if (c == '\n') result = 0 // expecting text to be whitespace-only line
      else if (c == '\t') result += tabSize
      else result += 1
      idx += 1
    }

    result
  }

  def calcSecondLineIndent(text: String, tabSize: Int): Int = {
    val newLineIdx = text.indexOf('\n')
    if (newLineIdx == -1) -1
    else calcIndent(text, newLineIdx + 1, tabSize)
  }

  def calcLastLineIndent(text: String, tabSize: Int): Int = {
    val idx = text.lastIndexOf('\n')
    calcIndent(text, idx + 1, tabSize)
  }

  def calcIndent(element: PsiElement, tabSize: Int): Int =
    element.getPrevNonEmptyLeaf match {
      case Whitespace(ws) => calcLastLineIndent(ws, tabSize)
      case _ => 0
    }

  @inline
  def compare(first: PsiElement, second: PsiElement, tabSize: Int): Int =
    calcIndent(first, tabSize) - calcIndent(second, tabSize)

  /**
   * @param indentString indentation string which can contain both tabs and spaces
   * @param spaces  number of extra spaces to append to `indentString`
   * @param tabSize      number of space chars to be replaces with a single tab char
   * @return new indent string value containing as much tab chars as possible in the end
   *
   * @example input: indentString ="[TAB][TAB][SPACE]", extraSpaces = 5, tabSize = 4
   *          return value: "[TAB][TAB][TAB][SPACE][SPACE]"
   */
  def appendSpacesToIndentString(indentString: String, spaces: Int, tabSize: Int): String = {
    val tabsCount = StringUtil.countChars(indentString, '\t')
    val totalIndentSize = tabsCount * tabSize + (indentString.length - tabsCount) + spaces
    IndentUtil.buildIndentString(totalIndentSize, tabSize)
  }

  def buildIndentString(spacesCount: Int, tabSize: Int): String = {
    val tabsCount = spacesCount / tabSize
    val tabs    = StringUtil.repeatSymbol('\t', tabsCount)
    val spaces  = StringUtil.repeatSymbol(' ', spacesCount - tabsCount * tabSize)
    tabs + spaces
  }
}
