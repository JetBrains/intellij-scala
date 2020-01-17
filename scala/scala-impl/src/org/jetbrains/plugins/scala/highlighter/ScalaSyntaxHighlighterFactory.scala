package org.jetbrains.plugins.scala
package highlighter

import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.{LanguageParserDefinitions, StdLanguages}
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutors
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scalaDoc.ScalaDocLanguage

final class ScalaSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

  import SyntaxHighlighterFactory.{getSyntaxHighlighter => findByLanguage}

  override def getSyntaxHighlighter(project: Project, file: VirtualFile): ScalaSyntaxHighlighter = {
    val language = if (project != null && file != null)
      LanguageSubstitutors.getInstance.substituteLanguage(ScalaLanguage.INSTANCE, file, project)
    else
      ScalaLanguage.INSTANCE

    val scalaLexer = LanguageParserDefinitions.INSTANCE
      .forLanguage(language)
      .createLexer(project)
      .asInstanceOf[ScalaLexer]

    new ScalaSyntaxHighlighter(
      new ScalaSyntaxHighlighter.CustomScalaLexer(scalaLexer),
      findByLanguage(ScalaDocLanguage.INSTANCE, project, file),
      findByLanguage(HTMLLanguage.INSTANCE, project, file)
    )
  }
}