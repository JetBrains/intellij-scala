package org.jetbrains.plugins.scala.editor.copy

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings

abstract class CopyPasteTestBase extends ScalaLightCodeInsightFixtureTestAdapter {
  protected val Start = EditorTestUtil.SELECTION_START_TAG
  protected val End = EditorTestUtil.SELECTION_END_TAG
  protected val Caret = EditorTestUtil.CARET_TAG

  val fromLangExtension: String = ".scala"

  private var oldSettings: ScalaCodeStyleSettings = _

  override protected def setUp(): Unit = {
    super.setUp()

    val project = getProject
    oldSettings = ScalaCodeStyleSettings.getInstance(project)
    TypeAnnotationSettings.set(project, TypeAnnotationSettings.alwaysAddType(oldSettings))
  }

  override def tearDown(): Unit = {
    TypeAnnotationSettings.set(getProject, oldSettings)
    super.tearDown()
  }

  protected def doTest(from: String, to: String, after: String): Unit = {
    def normalize(s: String): String = s.replace("\r", "")

    myFixture.configureByText(s"from.$fromLangExtension", normalize(from))
    myFixture.performEditorAction(IdeActions.ACTION_COPY)

    myFixture.configureByText("to.scala", normalize(to))
    myFixture.performEditorAction(IdeActions.ACTION_PASTE)

    myFixture.checkResult(normalize(after), true)
  }

  protected def doTestWithStrip(from: String, to: String, after: String): Unit = {
    doTest(from.stripMargin, to.stripMargin, after.stripMargin)
  }

  protected def doTestToEmptyFile(fromText: String, expectedText: String): Unit = {
    doTest(fromText, Caret, expectedText)
  }
}