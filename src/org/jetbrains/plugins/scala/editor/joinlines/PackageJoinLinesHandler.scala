package org.jetbrains.plugins.scala
package editor
package joinlines

import com.intellij.codeInsight.editorActions.{JoinLinesHandlerDelegate, JoinRawLinesHandlerDelegate}
import com.intellij.openapi.editor.Document
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging

/** `package a\npackageb` => `package a.b` */
class PackageJoinLinesHandler extends JoinRawLinesHandlerDelegate {
  // Not very robust if there is trailing whitespace on the first line.
  def tryJoinRawLines(document: Document, file: PsiFile, start: Int, end: Int): Int = {
    val elementAtStartLineEnd: PsiElement = file.findElementAt(start)
    val elementAtNextLineStart: PsiElement = file.findElementAt(end)
    if (elementAtNextLineStart == null || elementAtStartLineEnd == null) return JoinLinesHandlerDelegate.CANNOT_JOIN
    (elementAtStartLineEnd.getParent, elementAtNextLineStart.getParent) match {
      case (p0: ScPackaging, p1: ScPackaging) if p1.getParent == p0 =>
        p0.packageName
        document.replaceString(start, end + "package ".length(), ".")
        end
      case _ => JoinLinesHandlerDelegate.CANNOT_JOIN
    }
  }

  def tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int): Int = {
    JoinLinesHandlerDelegate.CANNOT_JOIN
  }
}
