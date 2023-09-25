package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionHighlighter
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiMethod}
import com.intellij.util.DocumentUtil
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

class ScalaSourcePositionHighlighter extends SourcePositionHighlighter {
  override def getHighlightRange(sourcePosition: SourcePosition): TextRange = {
    if (isScalaLanguage(sourcePosition)) {
      Option(sourcePosition.getElementAt)
        .flatMap(containingLambda)
        .flatMap(calculateRange(sourcePosition))
        .orNull
    }
    else null
  }

  private def isScalaLanguage(sourcePosition: SourcePosition): Boolean =
    sourcePosition.getFile.getLanguage.isKindOf(ScalaLanguage.INSTANCE)

  private def containingLambda(element: PsiElement): Option[PsiElement] =
    element.withParentsInFile.collectFirst {
      case e if ScalaPositionManager.isLambda(e) => Some(e)
      case _: PsiMethod => None
      case _: ScTemplateBody => None
      case _: ScEarlyDefinitions => None
      case _: ScClass => None
    }.flatten

  private def calculateRange(sourcePosition: SourcePosition)(lambda: PsiElement): Option[TextRange] =
    for {
      range <- Option(lambda.getTextRange)
      file = sourcePosition.getFile
      project = file.getProject
      document <- Option(PsiDocumentManager.getInstance(project).getDocument(file))
      lineRange = DocumentUtil.getLineTextRange(document, sourcePosition.getLine)
      res = range.intersection(lineRange) if lineRange != res
    } yield res
}
