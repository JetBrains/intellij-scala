package org.jetbrains.plugins.scala.highlighter

import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.{Language, LanguageParserDefinitions}
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutors
import org.jetbrains.annotations.{Nullable, TestOnly}
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.project.ModuleExt
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
  /**  Designed to be used in light tests with in-memory files, not belonging to any module */
  @TestOnly
  val LightTestModuleKey: Key[Module] = Key.create("light-test-module")

  def createScalaSyntaxHighlighter(@Nullable project: Project, @Nullable file: VirtualFile, language: Language): ScalaSyntaxHighlighter = {
    val scalaLexer = LanguageParserDefinitions.INSTANCE
      .forLanguage(language)
      .createLexer(project)
      .asInstanceOf[ScalaLexer]

    import SyntaxHighlighterFactory.{getSyntaxHighlighter => findByLanguage}

    //TODO: ideally this information should be stored in ScalaFeatures
    // and we should also first check the pushed features via ScalaFeaturePusher.getFeatures
    val noUnicodeEscapesInRawStrings = file != null && project != null && {
      val index = ProjectRootManager.getInstance(project).getFileIndex
      val module = index.getModuleForFile(file) match {
        case null => file.getUserData(LightTestModuleKey)
        case m => m
      }
      module != null && module.noUnicodeEscapesInRawStrings
    }

    val isScala3 = language.isKindOf(Scala3Language.INSTANCE)
    val customScalaLexer = new ScalaSyntaxHighlighter.CustomScalaLexer(
      scalaLexer,
      isScala3 = isScala3,
      noUnicodeEscapesInRawStrings = noUnicodeEscapesInRawStrings
    )
    new ScalaSyntaxHighlighter(
      customScalaLexer,
      findByLanguage(ScalaDocLanguage.INSTANCE, project, file),
      findByLanguage(ScalaDirectiveLanguage.INSTANCE, project, file),
      findByLanguage(HTMLLanguage.INSTANCE, project, file)
    )
  }
}