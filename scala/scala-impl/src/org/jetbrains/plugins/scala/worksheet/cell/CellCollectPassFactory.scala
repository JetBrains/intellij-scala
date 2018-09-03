package org.jetbrains.plugins.scala.worksheet.cell

import com.intellij.codeHighlighting.{Pass, TextEditorHighlightingPass, TextEditorHighlightingPassFactory, TextEditorHighlightingPassRegistrar}
import com.intellij.codeInsight.daemon.impl.DefaultHighlightInfoProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

/**
  * User: Dmitry.Naydanov
  * Date: 03.09.18.
  */
class CellCollectPassFactory(project: Project, highlightingPassRegistrar: TextEditorHighlightingPassRegistrar) extends TextEditorHighlightingPassFactory {
  highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null,
    null, false, 5)
  
  override def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass =
    file match {
      case scalaFile: ScalaFile if scalaFile.isWorksheetFile && WorksheetFileSettings.getRunType(file).isUsesCell =>
        val document = PsiDocumentManager.getInstance(project).getDocument(file)
        if (document != null) new CellCollectPass(file, editor, document, new DefaultHighlightInfoProcessor) else null
      case _ => null
    }
}
