package org.jetbrains.plugins.scala.util

import com.intellij.psi.PsiElement
import org.apache.commons.lang3.{StringUtils => ApacheStringUtils}
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

  def calcLastLineIndent(text: CharSequence, tabSize: Int): Int = {
    val idx = ApacheStringUtils.lastIndexOf(text, '\n')
    calcIndent(text, idx + 1, tabSize)
  }

  def calcIndent(element: PsiElement, tabSize: Int): Int =
    element.getPrevNonEmptyLeaf match {
      case Whitespace(ws) => calcLastLineIndent(ws, tabSize)
      case _ => 0
    }

  def calcRegionIndent(element: PsiElement, tabSize: Int): Int = {
    val elementOnNewLine =
      if (element.startsFromNewLine()) Some(element)
      else element.parents.find(_.startsFromNewLine())

    elementOnNewLine.fold(0)(calcIndent(_, tabSize))
  }

  @inline
  def compare(first: PsiElement, second: PsiElement, tabSize: Int): Int =
    calcIndent(first, tabSize) - calcIndent(second, tabSize)
}
