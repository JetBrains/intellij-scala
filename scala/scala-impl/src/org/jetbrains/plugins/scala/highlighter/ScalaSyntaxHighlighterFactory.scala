package org.jetbrains.plugins.scala
package highlighter

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
    new ScalaSyntaxHighlighter(scalaLexer)
  }
}