package org.jetbrains.plugins.scala
package editor.smartEnter

import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/**
 * Pavel Fatin
 */

class PackageSplitLinesProcessor extends SmartEnterProcessor {
  private val Package = "(\\s*)package .+".r

  def process(project: Project, editor: Editor, psiFile: PsiFile): Boolean = {
    val document = editor.getDocument
    val offset = editor.getCaretModel.getOffset

    val n = document.getLineNumber(offset)
    val start = document.getLineStartOffset(n)
    val end = document.getLineEndOffset(n)

    val line = document.getText(new TextRange(start, end))

    line match {
      case Package(prefix) =>
        val i = offset - start
        val dotIndex = line.indexOf('.', i)
        if (dotIndex == -1) false else {
          val tail = line.substring(dotIndex + 1)
          document.replaceString(start + dotIndex, end, "\n%spackage %s".format(prefix, tail))
          true
        }
      case _ => false
    }
  }
}