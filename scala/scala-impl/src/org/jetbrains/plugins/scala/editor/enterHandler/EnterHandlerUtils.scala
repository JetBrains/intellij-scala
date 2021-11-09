package org.jetbrains.plugins.scala.editor.enterHandler

import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.PsiElementExt

object EnterHandlerUtils {

  private[enterHandler] def precededIndentWhitespace(element: PsiElement): Option[PsiWhiteSpace] =
    element.getPrevNonEmptyLeaf match {
      case ws: PsiWhiteSpace if ws.textContains('\n') => Some(ws)
      case _                                          => None
    }

  /**
   * @return Some(indent) - if caret is in the beginning of the line (after some indent whitespace)<br>
   *         None - otherwise (if there is some code before the caret)
   * @todo can we move it to org.jetbrains.plugins.scala.util.IndentUtil, like calcIndent?
   */
  def calcCaretIndent(
    caretOffset: Int,
    documentText: CharSequence,
    tabSize: Int
  ): Option[Int]= {
    var indentSize = 0

    var continue = true
    var idx = caretOffset -1
    while (idx > 0 && continue) {
      val ch = documentText.charAt(idx)
      ch match {
        case ' '  => indentSize += 1
        case '\t' => indentSize += tabSize
        case '\n' =>
          continue = false
        case _ =>
          indentSize = -1
          continue = false
      }
      idx -= 1
    }

    if (indentSize == -1) None
    else Some(indentSize)
  }
}
