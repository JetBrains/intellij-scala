package org.jetbrains.plugins.scala.worksheet.runconfiguration

import java.util

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.{LineMarkerInfo, LineMarkerProvider}
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import com.intellij.util.NullableFunction
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetFileHook
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler

/**
  * User: Dmitry.Naydanov
  * Date: 27.02.17.
  */
class WorksheetLineMarkerProvider extends LineMarkerProvider {
  override def getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo[_ <: PsiElement] = {
    psiElement match {
      case _: PsiWhiteSpace | _: PsiComment => null
      case _ =>
        psiElement.getParent match {
          case scalaFile: ScalaFile if scalaFile.isWorksheetFile && WorksheetCompiler.isWorksheetReplMode(scalaFile) =>
            val project = scalaFile.getProject

            WorksheetFileHook.getEditorFrom(FileEditorManager.getInstance(project), scalaFile.getVirtualFile).flatMap {
              editor =>
                WorksheetCache.getInstance(project).getLastProcessedIncremental(editor).filter(
                  _ == editor.getDocument.getLineNumber(psiElement.getTextRange.getStartOffset)).map(_ => createArrowMarker(psiElement))
            }.orNull
          case _ => null
        }
    }
  }

  override def collectSlowLineMarkers(list: util.List[PsiElement],
                                      collection: util.Collection[LineMarkerInfo[_ <: PsiElement]]): Unit = {}

  private def createArrowMarker(psiElement: PsiElement) = {
    new LineMarkerInfo[PsiElement](psiElement, psiElement.getTextRange, AllIcons.Diff.CurrentLine, Pass.LINE_MARKERS,
      NullableFunction.NULL.asInstanceOf[com.intellij.util.Function[PsiElement, String]], null, GutterIconRenderer.Alignment.RIGHT)
  }
}