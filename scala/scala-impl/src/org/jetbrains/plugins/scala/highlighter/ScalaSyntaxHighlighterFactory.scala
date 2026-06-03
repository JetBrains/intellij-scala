package org.jetbrains.plugins.scala.highlighter

import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.{Language, LanguageParserDefinitions}
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{LanguageSubstitutors, PsiManager}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.project.{ScalaFeaturePusher, ScalaFeatures}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}
import org.jetbrains.plugins.scalaDirective.ScalaDirectiveLanguage
import org.jetbrains.plugins.scalaDoc.ScalaDocLanguage

/**
 * @see java analog in [[com.intellij.lang.java.JavaSyntaxHighlighterFactory]]
 */
final class ScalaSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

  override def getSyntaxHighlighter(
    @Nullable project: Project,
    @Nullable file: VirtualFile
  ): ScalaSyntaxHighlighter = {
    val language = if (project != null && file != null)
      LanguageSubstitutors.getInstance.substituteLanguage(ScalaLanguage.INSTANCE, file, project)
    else
      ScalaLanguage.INSTANCE

    createScalaSyntaxHighlighter(project, file, language)
  }
}

object ScalaSyntaxHighlighterFactory {

  def createScalaSyntaxHighlighter(
    @Nullable project: Project,
    @Nullable file: VirtualFile,
    language: Language
  ): ScalaSyntaxHighlighter = {
    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language)

    val isScala3 = language.isKindOf(Scala3Language.INSTANCE)

    // SyntaxHighlighter instances can outlive sbt/project model refreshes.
    // Re-read pushed Scala features for every lexer, so raw-string highlighting follows the refreshed SDK.
    def createScalaLexer(): ScalaSyntaxHighlighter.CustomScalaLexer = {
      val featuresFromFile = getScalaFeaturesForFile(project, file)
      val features = featuresFromFile.getOrElse(ScalaFeatures.defaultForLanguage(language))
      val noUnicodeEscapesInRawStrings = features.noUnicodeEscapesInRawStrings

      new ScalaSyntaxHighlighter.CustomScalaLexer(
        parserDefinition.createLexer(project).asInstanceOf[ScalaLexer],
        isScala3 = isScala3,
        noUnicodeEscapesInRawStrings = noUnicodeEscapesInRawStrings
      )
    }

    import SyntaxHighlighterFactory.getSyntaxHighlighter

    new ScalaSyntaxHighlighter(
      createScalaLexer,
      getSyntaxHighlighter(ScalaDocLanguage.INSTANCE, project, file), // TODO: Switch highlighting lexer depending on markdown/wikidoc
      getSyntaxHighlighter(ScalaDirectiveLanguage.INSTANCE, project, file),
      getSyntaxHighlighter(HTMLLanguage.INSTANCE, project, file),
      isScala3 = isScala3,
    )
  }

  private def getScalaFeaturesForFile(
    @Nullable project: Project,
    @Nullable file: VirtualFile,
  ): Option[ScalaFeatures] = inReadAction {
    val psiFile = if (project != null && file != null) {
      // If we try to search for a file in a non-valid state, we get an exception that fails tests "Accessing invalid virtual file ..."
      // Known reasons when the file might be non-valid:
      //  1. Synthetical file created during language injection (example: "VirtualFileWindow in /src/A.scala")<br>
      //     (see com.intellij.psi.impl.source.tree.injected.InjectionRegistrarImpl)
      //     Language injection can happen in many places: string literals in Scala code, Markdown code snippets with Scala language, etc...
      if (file.isValid)
        PsiManager.getInstance(project).findFile(file)
      else
        null
    } else
      null

    if (psiFile != null)
      ScalaFeaturePusher.getFeatures(psiFile)
    else
       None
  }
}
