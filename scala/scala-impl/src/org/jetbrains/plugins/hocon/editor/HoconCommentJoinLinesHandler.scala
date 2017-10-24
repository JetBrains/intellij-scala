package org.jetbrains.plugins.hocon.editor

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate
import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.plugins.hocon.CommonUtil
import org.jetbrains.plugins.hocon.lexer.HoconTokenSets
import org.jetbrains.plugins.hocon.psi.HoconPsiFile

/**
  * HOCON line comments can start with either '//' or '#'. Unfortunately, only one of them can be declared in
  * [[HoconCommenter]] and so I need this custom join lines handler to properly handle both.
  */
class HoconCommentJoinLinesHandler extends JoinLinesHandlerDelegate {
  def tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int): Int = file match {
    case _: HoconPsiFile =>
      import CommonUtil._
      val element = file.findElementAt(start)
      if (element != null && HoconTokenSets.Comment.contains(element.getNode.getElementType)) {
        val joinedSequence = document.getCharsSequence.subSequence(end, document.getTextLength)
        List("#", "//").find(joinedSequence.startsWith).map { nextPrefix =>
          val toRemoveEnd = CharArrayUtil.shiftForward(document.getCharsSequence,
            end + nextPrefix.length, element.getTextRange.getEndOffset, " \t")
          document.replaceString(start + 1, toRemoveEnd, " ")
          start + 1
        } getOrElse CANNOT_JOIN
      } else CANNOT_JOIN
    case _ => CANNOT_JOIN
  }
}
