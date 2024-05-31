package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.errorHighlighting

import com.intellij.modcommand.ActionContext
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.surroundWith.ScalaModCommandSurrounder

class IgnoreErrorHighlightingSurrounder extends ScalaModCommandSurrounder {
  //noinspection ScalaExtractStringToBundle
  override def getTemplateDescription: String = "/*_*/.../*_*/"

  override def isApplicable(elements: Array[PsiElement]): Boolean = elements.nonEmpty

  override def surroundElements(elements: Array[PsiElement], context: ActionContext): Option[TextRange] = {
    val firstElement = elements.head
    firstElement.containingFile.map { file =>
      val text = "/*_*/"
      val start = firstElement.startOffset
      val end = elements.last.endOffset

      val document = file.getFileDocument
      document.insertString(end, text)
      document.insertString(start, text)

      new TextRange(end + 2 * text.length, end + 2 * text.length)
    }
  }
}
