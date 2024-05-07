package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionHighlighter
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiMethod}
import com.intellij.util.DocumentUtil
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

class ScalaSourcePositionHighlighter extends SourcePositionHighlighter {
  override def getHighlightRange(sourcePosition: SourcePosition): TextRange = {
    if (isScalaLanguage(sourcePosition)) {
      val element = sourcePosition.getElementAt
      if (element eq null) return null

      val document = sourcePosition.getFile.getViewProvider.getDocument
      if (document eq null) return null

      val lineRange = DocumentUtil.getLineTextRange(document, sourcePosition.getLine)

      if (isWholeLine(lineRange, element)) return null

      containingLambda(element).flatMap(calculateRange(lineRange)).orNull
    }
    else null
  }

  private def isScalaLanguage(sourcePosition: SourcePosition): Boolean =
    sourcePosition.getFile.getLanguage.isKindOf(ScalaLanguage.INSTANCE)

  private def isWholeLine(lineRange: TextRange, element: PsiElement): Boolean =
    lineRange == element.getTextRange

  private def containingLambda(element: PsiElement): Option[PsiElement] =
    element.withParentsInFile.collectFirst {
      case e if ScalaPositionManager.isLambda(e) => Some(e)
      case _: PsiMethod => None
      case _: ScTemplateBody => None
      case _: ScEarlyDefinitions => None
      case _: ScClass => None
    }.flatten

  private def calculateRange(lineRange: TextRange)(lambda: PsiElement): Option[TextRange] = {
    val intersection = lambda.getTextRange.intersection(lineRange)
    if (lineRange != intersection) Some(intersection) else None
  }
}
