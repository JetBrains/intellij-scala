package org.jetbrains.plugins.scala.lang.lexer.highlightingLexer

import com.intellij.lang.Language
import com.intellij.lexer.Lexer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexerTestBase
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaFeaturePusher, ScalaFeatures, ScalaModuleSettings}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaFileType, ScalaVersion}

abstract class ScalaHighlightingLexerTestBase extends ScalaLexerTestBase {
  private val LOG = Logger.getInstance(getClass)

  protected def scalaVersion: ScalaVersion

  protected def additionalCompilerOptions: Seq[String] = Seq.empty

  override protected def createLexer: Lexer = {
    val scalaFeatures = configureScalaFeatures
    LOG.info(
      s"ScalaHighlightingLexerTestBase.createLexer: scalaVersion=$scalaVersion, " +
        s"languageLevel=${scalaFeatures.languageLevel}, " +
        s"noUnicodeEscapesInRawStrings=${scalaFeatures.noUnicodeEscapesInRawStrings}"
    )
    val virtualFile = createScalaFileWithFeatures(scalaFeatures)
    createHighlightingLexer(virtualFile)
  }

  private def configureScalaFeatures: ScalaFeatures.SerializableScalaFeatures = {
    val module = getModule
    configureModuleScalaVersionAndAdditionalCompilerOptions(module)
    //Q: Could we try to avoid configureModuleScalaVersionAndAdditionalCompilerOptions?
    // Can't we directly construct the features from the `scalaVersion`and `additionalCompilerOptions`?
    module.featuresNonDefault
  }

  private def configureModuleScalaVersionAndAdditionalCompilerOptions(module: Module): Unit = {
    ScalaModuleSettings.TestUtils.setModuleScalaVersionForLightTests(module, scalaVersion)

    if (additionalCompilerOptions.nonEmpty) {
      val profile = ScalaCompilerSettingsProfile.forModule(module)
      val newSettings = profile.getSettings.copy(additionalCompilerOptions = additionalCompilerOptions)
      profile.setSettings(newSettings)
    }
  }

  private def createScalaFileWithFeatures(scalaFeatures: ScalaFeatures.SerializableScalaFeatures): LightVirtualFile = {
    val virtualFile = new LightVirtualFile("dummy.scala", ScalaFileType.INSTANCE, "")
    ScalaFeaturePusher.setFeatures(virtualFile, scalaFeatures)
    virtualFile
  }

  private def createHighlightingLexer(virtualFile: LightVirtualFile) = {
    val scalaSyntaxHighlighter = ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter(project, virtualFile, language)
    scalaSyntaxHighlighter.getHighlightingLexer
  }
}

abstract class ScalaHighlightingLexerTestBase_Scala3 extends ScalaHighlightingLexerTestBase {
  override protected def language: Language = Scala3Language.INSTANCE

  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3
}
