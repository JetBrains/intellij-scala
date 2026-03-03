package org.jetbrains.plugins.scala.highlighter

import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.{Language, LanguageParserDefinitions}
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutors
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.project.{ScalaFeaturePusher, ScalaFeatures}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}
import org.jetbrains.plugins.scalaDirective.ScalaDirectiveLanguage
import org.jetbrains.plugins.scalaDoc.ScalaDocLanguage

final class ScalaSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

  override def getSyntaxHighlighter(project: Project, file: VirtualFile): ScalaSyntaxHighlighter = {
    val language = if (project != null && file != null)
      LanguageSubstitutors.getInstance.substituteLanguage(ScalaLanguage.INSTANCE, file, project)
    else
      ScalaLanguage.INSTANCE

    createScalaSyntaxHighlighter(project, file, language)
  }
}

object ScalaSyntaxHighlighterFactory {

  def createScalaSyntaxHighlighter(@Nullable project: Project, @Nullable file: VirtualFile, language: Language): ScalaSyntaxHighlighter = {
    val scalaLexer = LanguageParserDefinitions.INSTANCE
      .forLanguage(language)
      .createLexer(project)
      .asInstanceOf[ScalaLexer]

    import SyntaxHighlighterFactory.{getSyntaxHighlighter => findByLanguage}

    val features = getPushedFeaturesOrDefault(file, language)
    val noUnicodeEscapesInRawStrings = features.noUnicodeEscapesInRawStrings

    val isScala3 = language.isKindOf(Scala3Language.INSTANCE)
    val customScalaLexer = new ScalaSyntaxHighlighter.CustomScalaLexer(
      scalaLexer,
      isScala3 = isScala3,
      noUnicodeEscapesInRawStrings = noUnicodeEscapesInRawStrings
    )
    new ScalaSyntaxHighlighter(
      customScalaLexer,
      findByLanguage(ScalaDocLanguage.INSTANCE, project, file), // TODO: Switch highlighting lexer depending on markdown/wikidoc
      findByLanguage(ScalaDirectiveLanguage.INSTANCE, project, file),
      findByLanguage(HTMLLanguage.INSTANCE, project, file)
    )
  }

  private def getPushedFeaturesOrDefault(@Nullable file: VirtualFile, language: Language): ScalaFeatures = {
    val fromPusher = Option(file).flatMap(ScalaFeaturePusher.getFeatures)
    fromPusher.getOrElse(ScalaFeatures.defaultForLanguage(language))
  }
}