package org.jetbrains.plugins.scala.actions

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.actions.ShowTypeInfoAction.ActionId
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.EditorHintFixtureEx
import org.junit.Assert.assertEquals

class ShowTypeInfoActionTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_2_13

  private var editorHintFixture: EditorHintFixtureEx = _

  override protected def setUp(): Unit = {
    super.setUp()
    editorHintFixture = new EditorHintFixtureEx(this.getTestRootDisposable)
  }

  private def doShowTypeInfoTest(
    fileText: String,
    expectedTypeInfoHintBodyContent: String,
  ): Unit = {
    configureFromFileText(fileText)
    getFixture.performEditorAction(ActionId)

    assertEquals(
      expectedTypeInfoHintBodyContent.trim,
      editorHintFixture.getCurrentHintBodyText.trim
    )
  }

  def testTypeAlias_String(): Unit = doShowTypeInfoTest(
    s"type ${CARET}T = String",
    "String"
  )

  def testTypeAlias_Map(): Unit = doShowTypeInfoTest(
    s"type ${CARET}T[A] = Map[A, String]",
    "Map[A, String]"
  )
}