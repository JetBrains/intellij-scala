package org.jetbrains.plugins.scala.tasty

import com.intellij.openapi.fileTypes.{FileType, SyntaxHighlighter, SyntaxHighlighterFactory, SyntaxHighlighterProvider}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter

class TastySyntaxHighlighterFactory extends SyntaxHighlighterFactory with SyntaxHighlighterProvider {
  override def getSyntaxHighlighter(project: Project, virtualFile: VirtualFile): SyntaxHighlighter = null

  override def create(fileType: FileType, project: Project, file: VirtualFile): SyntaxHighlighter = {
    if (fileType == TastyFileType) createScalaSyntaxHighlighter(project, file, Scala3Language.INSTANCE)
    else null
  }
}