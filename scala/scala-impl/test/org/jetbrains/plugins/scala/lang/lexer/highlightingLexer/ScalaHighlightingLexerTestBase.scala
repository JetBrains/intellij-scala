package org.jetbrains.plugins.scala.lang.lexer.highlightingLexer

import com.intellij.lang.Language
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.IndexingTestUtil
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexerTestBase
import org.jetbrains.plugins.scala.project.ScalaModuleSettings
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile
import org.jetbrains.plugins.scala.{Scala3Language, ScalaVersion}

abstract class ScalaHighlightingLexerTestBase extends ScalaLexerTestBase {

  protected def scalaVersion: ScalaVersion

  protected def additionalCompilerOptions: Seq[String] = Seq.empty

  override protected def createLexer: Lexer = {
    configureModuleScalaVersionAndAdditionalCompilerOptions()

    val virtualFile = createScalaFileInModuleSourceRoot()
    createHighlightingLexer(virtualFile)
  }

  private def configureModuleScalaVersionAndAdditionalCompilerOptions(): Unit = inWriteAction {
    val module = getModule
    val project = module.getProject

    // Touch the module root model so IntelliJ reruns file property pushers and indexes after module-level settings change.
    // We need this to ensure the ScalaFeatures are recalculated correctly.
    ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(() => {
      ScalaModuleSettings.TestUtils.setModuleScalaVersionForLightTests(module, scalaVersion)

      if (additionalCompilerOptions.nonEmpty) {
        val profile = ScalaCompilerSettingsProfile.forModule(module)
        val newSettings = profile.getSettings.copy(additionalCompilerOptions = additionalCompilerOptions)
        profile.setSettings(newSettings)
      }
    }, RootsChangeRescanningInfo.TOTAL_RESCAN)

    // Ensure we wait for the files to be indexed
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  protected def scalaFileName: String = "example.scala"

  private def createScalaFileInModuleSourceRoot(): VirtualFile = inWriteAction {
    val sourceRoot = ModuleRootManager.getInstance(getModule).getSourceRoots.head
    sourceRoot.createChildData(this, scalaFileName)
  }

  private def createHighlightingLexer(virtualFile: VirtualFile) = {
    val scalaSyntaxHighlighter = ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter(project, virtualFile, language)
    scalaSyntaxHighlighter.getHighlightingLexer
  }
}

abstract class ScalaHighlightingLexerTestBase_Scala3 extends ScalaHighlightingLexerTestBase {
  override protected def language: Language = Scala3Language.INSTANCE

  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3
}
