package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.{LibraryOrderEntry, ModuleRootManager, ModuleRootModificationUtil}
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.psi.StringEscapesTokenTypes
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.IndexingTestUtil
import org.jetbrains.plugins.scala.base.libraryLoaders.MockScalaSDKLoader
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.highlighter.{ScalaSyntaxHighlighter, ScalaSyntaxHighlighterFactory}
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.plugins.scala.{ScalaLanguage, ScalaVersion}

import scala.collection.mutable

/**
 * This is a more integrational test for ensuring that scala highlighter is recreated on any project model change
 *
 * Also [[org.jetbrains.plugins.scala.lang.lexer.LexerCreationTest]] for a more unit-test-oriented test
 */
class ScalaSyntaxHighlighterProjectModelChangeIntegrationTest
  extends ScalaLightCodeInsightFixtureTestCase
    with AssertionMatchers {

  override protected def loadScalaLibrary: Boolean = false

  override protected def sharedProjectToken: SharedTestProjectToken =
    SharedTestProjectToken.DoNotShare

  def testRawUnicodeEscapeHighlightingFollowsScalaSdkChange(): Unit = {
    val fileText = "raw\"\\u0041\""
    val scala2ExpectedHighlightingTokens = Seq(
      (ScalaTokenTypes.tINTERPOLATED_STRING_ID, "raw"),
      (ScalaTokenTypes.tINTERPOLATED_STRING, "\""),
      (StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, "\\u0041"),
      (ScalaTokenTypes.tINTERPOLATED_STRING_END, "\""),
    )
    val scala3ExpectedHighlightingTokens = Seq(
      (ScalaTokenTypes.tINTERPOLATED_STRING_ID, "raw"),
      (ScalaTokenTypes.tINTERPOLATED_STRING, "\""),
      (ScalaTokenTypes.tINTERPOLATED_STRING, "\\u0041"),
      (ScalaTokenTypes.tINTERPOLATED_STRING_END, "\""),
    )

    replaceScalaSdkWith(ScalaVersion.Latest.Scala_2_13)

    val virtualFile = createScalaFileInModuleSourceRoot(fileText)

    // Reuse the same highlighter instance to model editor-side highlighter caching across project refreshes.
    val highlighter = ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter(
      project = getProject,
      file = virtualFile,
      language = ScalaLanguage.INSTANCE
    )

    assertCollectionEquals(
      "Highlighting tokens for Scala 2 (before any changes in Scala SDK)",
      scala2ExpectedHighlightingTokens,
      collectAllLexerTokens(highlighter, fileText)
    )

    replaceScalaSdkWith(ScalaVersion.Latest.Scala_3)
    assertCollectionEquals(
      "Highlighting tokens for Scala 3 (after changing Scala SDK from Scala 2 to Scala 3)",
      scala3ExpectedHighlightingTokens,
      collectAllLexerTokens(highlighter, fileText)
    )

    replaceScalaSdkWith(ScalaVersion.Latest.Scala_2_13)
    assertCollectionEquals(
      "Highlighting tokens for Scala 2 (after changing Scala SDK from Scala 3 to Scala 2)",
      scala2ExpectedHighlightingTokens,
      collectAllLexerTokens(highlighter, fileText)
    )
  }

  private def replaceScalaSdkWith(scalaVersion: ScalaVersion): Unit = {
    val module = getModule
    val project = getProject

    inWriteAction {
      // Use a real roots change so file property pushers and root-dependent caches observe the SDK replacement.
      ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(() => {
        removeScalaSdk(module)
        addMockScalaSdk(module, scalaVersion)
      }, RootsChangeRescanningInfo.TOTAL_RESCAN)
    }

    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  private def removeScalaSdk(module: Module): Unit =
    ModuleRootModificationUtil.updateModel(module, model => {
      val scalaSdkEntry = model.getOrderEntries.collectFirst {
        case entry: LibraryOrderEntry if Option(entry.getLibrary).exists(_.isScalaSdk) => entry
      }
      scalaSdkEntry.foreach(model.removeOrderEntry)
    })

  private def addMockScalaSdk(module: Module, scalaVersion: ScalaVersion): Unit =
    new MockScalaSDKLoader().init(module, scalaVersion)

  private def createScalaFileInModuleSourceRoot(text: String): VirtualFile = inWriteAction {
    val virtualFile = moduleSourceRoot.createChildData(this, "RawUnicodeEscape.scala")
    VfsUtil.saveText(virtualFile, text)
    virtualFile
  }

  private def moduleSourceRoot: VirtualFile =
    ModuleRootManager.getInstance(getModule).getSourceRoots.head

  private def collectAllLexerTokens(
    highlighter: ScalaSyntaxHighlighter,
    text: String
  ): Seq[(IElementType, String)] = {
    val lexer = highlighter.getHighlightingLexer
    lexer.start(text)

    val buffer = mutable.ArrayBuffer[(IElementType, String)]()
    while (lexer.getTokenType != null) {
      buffer += lexer.getTokenType -> lexer.getTokenText
      lexer.advance()
    }
    buffer.toSeq
  }
}
