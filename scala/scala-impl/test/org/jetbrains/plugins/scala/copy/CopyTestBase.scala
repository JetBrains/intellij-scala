package org.jetbrains.plugins.scala.copy

import com.intellij.openapi.actionSystem.IdeActions.{ACTION_COPY, ACTION_PASTE}
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings

/**
  * Created by Kate Ustyuzhanina on 12/28/16.
  */
abstract class CopyTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  import CodeInsightTestFixture.CARET_MARKER
  import ScalaLightCodeInsightFixtureTestAdapter._

  var oldSettings: ScalaCodeStyleSettings = _

  override protected def setUp(): Unit = {
    super.setUp()

    val project = getProject
    oldSettings = ScalaCodeStyleSettings.getInstance(project)
    TypeAnnotationSettings.set(project, TypeAnnotationSettings.alwaysAddType(oldSettings))
  }

  protected def doTest(fromText: String, toText: String, expectedText: String): Unit = {
    myFixture.configureByText(s"fromDummy$fromLangExtension", normalize(fromText))
    myFixture.performEditorAction(ACTION_COPY)

    myFixture.configureByText("toDummy.scala", normalize(toText + CARET_MARKER))
    myFixture.performEditorAction(ACTION_PASTE)

    myFixture.checkResult(normalize(expectedText))
  }

  protected def doTestEmptyToFile(fromText: String, expectedText: String): Unit = {
    doTest(fromText, CARET_MARKER, expectedText)
  }

  override def tearDown(): Unit = {
    TypeAnnotationSettings.set(getProject, oldSettings)

    super.tearDown()
  }

  val fromLangExtension: String
}