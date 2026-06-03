package org.jetbrains.plugins.scala.editor.joinLines

import com.intellij.codeInsight.editorActions.{JoinLinesHandlerDelegate, JoinRawLinesHandlerDelegate}
import com.intellij.openapi.editor.Document
import com.intellij.psi._
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.Parent
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral

import scala.util.matching.Regex

class StripMarginJoinLinesHandler extends JoinRawLinesHandlerDelegate {
  private val Prefix = new Regex("\n\\s*\\|")

  override def tryJoinRawLines(document: Document, file: PsiFile, start: Int, end: Int): Int = (file.findElementAt(start), file.findElementAt(end)) match {
    case (Parent(s1: ScStringLiteral), Parent(s2: ScStringLiteral)) if s1 == s2 && s1.isMultiLineString =>
      val firstLineEnd = document.lineEndOffset(start)
      val lastLineEnd = (document.lineEndOffset(end) + 1).min(document.getTextLength)
      val text = document.getCharsSequence.subSequence(firstLineEnd, lastLineEnd)
      if (Prefix.findFirstIn(text).isDefined) {
        document.replaceString(firstLineEnd, lastLineEnd, Prefix.replaceAllIn(text, ""))
        start
      } else {
        JoinLinesHandlerDelegate.CANNOT_JOIN
      }
    case _ =>
      JoinLinesHandlerDelegate.CANNOT_JOIN
  }

  override def tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int): Int = JoinLinesHandlerDelegate.CANNOT_JOIN
}
