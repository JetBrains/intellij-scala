package org.jetbrains.plugins.scala.copy

import com.intellij.openapi.actionSystem.IdeActions
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TestUtils.CARET_MARKER
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings

/**
  * Created by Kate Ustyuzhanina on 12/28/16.
  */
abstract class CopyTestBase() extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaLightCodeInsightFixtureTestAdapter._

  var oldSettings: ScalaCodeStyleSettings = _

  override protected def setUp(): Unit = {
    super.setUp()

    oldSettings = ScalaCodeStyleSettings.getInstance(getProject)
    TypeAnnotationSettings.set(getProject, TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject)))
  }

  def doTest(fromText: String, toText: String, expectedText: String): Unit = {
    myFixture.configureByText("from" + "Dummy" + fromLangExtension, normalize(fromText))
    myFixture.performEditorAction(IdeActions.ACTION_COPY)
    myFixture.configureByText("to" + "Dummy" + toLangExtension, normalize(toText))
    myFixture.performEditorAction(IdeActions.ACTION_PASTE)
    myFixture.checkResult(expectedText)
  }

  def doTestEmptyToFile(fromText: String, expectedText: String): Unit = {
    doTest(fromText, CARET_MARKER, expectedText)
  }

  override def tearDown(): Unit = {
    TypeAnnotationSettings.set(getProject, oldSettings)

    super.tearDown()
  }

  val toLangExtension: String = ".scala"
  val fromLangExtension: String
}