package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.errorHighlighting

import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * @author Alefas
 * @since 10.04.12
 */

class IgnoreErrorHighlightingSurrounder extends Surrounder {
  def getTemplateDescription: String = "/*_*/.../*_*/"

  def isApplicable(elements: Array[PsiElement]): Boolean = true

  def surroundElements(project: Project, editor: Editor, elements: Array[PsiElement]): TextRange = {
    val start = elements(0).getTextRange.getStartOffset
    val end = elements(elements.length - 1).getTextRange.getEndOffset
    val text = "/*_*/"
    editor.getDocument.insertString(end, text)
    editor.getDocument.insertString(start, text)
    new TextRange(end + 2 * text.length, end + 2 * text.length)
  }
}
