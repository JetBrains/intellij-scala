package org.jetbrains.plugins.scala.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, Whitespace}

object IndentUtil {
  private def calcIndent(text: CharSequence, offset: Int, tabSize: Int): Int = {
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
}
