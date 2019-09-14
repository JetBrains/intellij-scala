package org.jetbrains.plugins.scala
package highlighter

import com.intellij.lang.StdLanguages
import com.intellij.lexer.BaseHtmlLexer
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

final class ScalaSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

  import project._

  override def getSyntaxHighlighter(project: Project, file: VirtualFile): ScalaSyntaxHighlighter = {
    val isScala3 = if (project != null && file != null)
      file.isScala3(project)
    else
      false

    val scalaLexer = new ScalaSyntaxHighlighter.CustomScalaLexer(isScala3)(project)

    val scalaDocLexer = ScalaEditorHighlighterProvider
      .ScalaDocSyntaxHighlighter
      .INSTANCE
      .getHighlightingLexer

    val htmlLexer = SyntaxHighlighterFactory
      .getSyntaxHighlighter(StdLanguages.HTML, project, file)
      .getHighlightingLexer
      .asInstanceOf[BaseHtmlLexer]

    new ScalaSyntaxHighlighter(scalaLexer, scalaDocLexer, htmlLexer)
  }
}