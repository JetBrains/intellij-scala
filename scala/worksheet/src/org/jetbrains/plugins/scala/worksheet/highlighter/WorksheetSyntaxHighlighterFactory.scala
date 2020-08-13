package org.jetbrains.plugins.scala.worksheet.highlighter

import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutors
import org.jetbrains.plugins.scala.highlighter.{ScalaSyntaxHighlighter, ScalaSyntaxHighlighterFactory}
import org.jetbrains.plugins.scala.worksheet.WorksheetLanguage

final class WorksheetSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

  override def getSyntaxHighlighter(project: Project, file: VirtualFile): ScalaSyntaxHighlighter = {
    val language = if (project == null || file == null)
      WorksheetLanguage.INSTANCE
    else
      LanguageSubstitutors.getInstance.substituteLanguage(WorksheetLanguage.INSTANCE, file, project)

    ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter(project, file, language)
  }
}