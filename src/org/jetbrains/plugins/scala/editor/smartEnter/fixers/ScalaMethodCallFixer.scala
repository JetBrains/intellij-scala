package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.openapi.editor.Editor
import com.intellij.psi._
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

/**
 * @author Dmitry.Naydanov
 * @author Ksenia.Sautina
 * @since 1/31/13
 */
class ScalaMethodCallFixer extends ScalaFixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement): OperationPerformed = {
    val args = psiElement match {
      case call: ScMethodCall => call.args
      case _ => return NoOp()
    }

    if (args.lastChild.exists(_.getText == ")")) return NoOp()

    var endOffset: Int = -1
    var child = args.firstChild.orNull
    var flag = true

    while (child != null && flag) {
      child match {
        case errorElement: PsiErrorElement =>
          if (errorElement.getErrorDescription.indexOf("')'") >= 0) {
            endOffset = errorElement.getTextRange.getStartOffset
            flag = false
          }
        case _ =>
      }

      child = child.getNextSibling
    }

    if (endOffset == -1) endOffset = args.getTextRange.getEndOffset

    val params = args.exprs
    if (params.nonEmpty && startLine(editor, args) != startLine(editor, params.head))
      endOffset = args.getTextRange.getStartOffset + 1

    endOffset = CharArrayUtil.shiftBackward(editor.getDocument.getCharsSequence, endOffset - 1, " \t\n") + 1
    editor.getDocument.insertString(endOffset, ")")

    WithReformat(1)
  }
}

