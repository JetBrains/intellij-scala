package org.jetbrains.plugins.scala
package highlighter

import com.intellij.lang.StdLanguages
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scalaDoc.ScalaDocLanguage

final class ScalaSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

  import SyntaxHighlighterFactory.{getSyntaxHighlighter => findByLanguage}
  import project._

  override def getSyntaxHighlighter(project: Project, file: VirtualFile): ScalaSyntaxHighlighter = {
    val isScala3 = if (project != null && file != null)
      file.isScala3(project)
    else
      false

    new ScalaSyntaxHighlighter(
      new ScalaSyntaxHighlighter.CustomScalaLexer(isScala3)(project),
      findByLanguage(ScalaDocLanguage.INSTANCE, project, file),
      findByLanguage(StdLanguages.HTML, project, file)
    )
  }
}