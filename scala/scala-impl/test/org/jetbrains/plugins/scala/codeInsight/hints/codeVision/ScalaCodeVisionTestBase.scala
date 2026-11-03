package org.jetbrains.plugins.scala.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.utils.codeVision.CodeVisionTestCase
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert.assertEquals

import scala.jdk.CollectionConverters._

abstract class ScalaCodeVisionTestBase extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_13

  /** Group ids of code vision providers which are enabled in the test, all others are disabled */
  protected def enabledGroupIds: Set[String]

  private def codeVisionHost: CodeVisionHost =
    getProject.getService(classOf[CodeVisionHost])

  override protected def setUp(): Unit = {
    super.setUp()
    Registry.get("editor.codeVision.new").setValue(true, getTestRootDisposable)
    TestModeFlags.set(CodeVisionHost.Companion.isCodeVisionTestKey, java.lang.Boolean.TRUE, getTestRootDisposable)
  }

  override protected def tearDown(): Unit = {
    try {
      val settings = CodeVisionSettings.getInstance()
      codeVisionHost.getProviders.asScala.foreach(provider => settings.setProviderEnabled(provider.getGroupId, true))
    } finally {
      super.tearDown()
    }
  }

  /** @param expected file text with code vision inlays, in the format of [[InlayDumpUtil]] */
  protected def doTest(expected: String): Unit = {
    val settings = CodeVisionSettings.getInstance()
    val host = codeVisionHost
    host.getProviders.asScala.map(_.getGroupId).toSet.foreach { (groupId: String) =>
      settings.setProviderEnabled(groupId, enabledGroupIds.contains(groupId))
    }

    val sourceText = InlayDumpUtil.INSTANCE.removeInlays(expected)
    configureFromFileText(sourceText)
    getFixture.doHighlighting()

    assertHints(expected)
  }

  /** Inserts `text` right after the first occurrence of `anchor` and re-runs highlighting. */
  protected def insertAfter(anchor: String, text: String): Unit = {
    WriteCommandAction.runWriteCommandAction(getProject, (() => {
      val document = getEditor.getDocument
      val offset = document.getText.indexOf(anchor)
      assert(offset >= 0, s"'$anchor' not found")
      document.insertString(offset + anchor.length, text)
      PsiDocumentManager.getInstance(getProject).commitDocument(document)
    }): Runnable)
    getFixture.doHighlighting()
  }

  protected def assertHints(expected: String): Unit = {
    CodeVisionTestCase.waitForCodeVisionSync(getEditor, codeVisionHost, getTestRootDisposable)
    val actual = CodeVisionTestCase.dumpCodeVisionHints(getEditor.getDocument.getText, getEditor, false)
    assertEquals(expected, actual)
  }
}
