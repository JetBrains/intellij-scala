package org.jetbrains.plugins.scala.lang.actions.editor.copy

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
import org.junit.Assert.assertTrue

abstract class CopyPasteTestBase extends ScalaLightCodeInsightFixtureTestAdapter {
  protected val Start = EditorTestUtil.SELECTION_START_TAG
  protected val End = EditorTestUtil.SELECTION_END_TAG
  protected val Caret = EditorTestUtil.CARET_TAG

  protected val tab = "\t"
  protected val empty = ""

  def fromLangExtension: String = ".scala"

  private var oldSettings: ScalaCodeStyleSettings = _
  private var oldBlankLineSetting: Int = _

  override protected def setUp(): Unit = {
    super.setUp()

    val project = getProject
    oldSettings = ScalaCodeStyleSettings.getInstance(project)
    oldBlankLineSetting = oldSettings.BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES
    oldSettings.BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES = 0
    TypeAnnotationSettings.set(project, TypeAnnotationSettings.alwaysAddType(oldSettings))
  }

  override def tearDown(): Unit = {
    val project = getProject
    ScalaCodeStyleSettings.getInstance(project).BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES = oldBlankLineSetting
    TypeAnnotationSettings.set(project, oldSettings)
    super.tearDown()
  }

  protected final def doTest(from: String, to: String, after: String): Unit = {
    doTest(from, to, after, s"from.$fromLangExtension", "to.scala")
  }

  protected def doTest(from: String, to: String, after: String, fromFileName: String, toFileName: String): Unit = {
    def normalize(s: String): String = s.replace("\r", "")

    assertTrue("Content of target file doesn't contain caret marker", to.contains(Caret))

    myFixture.configureByText(fromFileName, normalize(from))
    myFixture.performEditorAction(IdeActions.ACTION_COPY)

    myFixture.configureByText(toFileName, normalize(to))
    myFixture.performEditorAction(IdeActions.ACTION_PASTE)

    myFixture.checkResult(normalize(after), true)
  }

  /**
   * The test tests that with any existing selection in the editor,
   * when we paste some code the selected code should be removed and replaced with the pasted code.
   * So it shouldn't matter which selection is there in the editor, the result should be the same
   */
  protected def doTestWithAllSelections(from: String, to: String, after: String): Unit = {
    val textsWithSelections = Seq(
      s"""$Caret""",
      s"""$Caret$Start$End""",
      s"""$Caret$Start  $End""",
      s"""$Caret$Start$tab$End""",
      s"""$Caret$Start
         |$End""".stripMargin,
      s"""$Caret$Start$tab$empty
         |  $End""".stripMargin,
      s"""$Caret${Start}print("Existing code 1")$End""",
      s"""$Caret$Start
         |  print("Existing code 2")$tab$empty
         | $End""".stripMargin,
    )

    val uniqueToken = System.currentTimeMillis
    for (case (textWithSelection, index) <- textsWithSelections.zipWithIndex) {
      val toModified = to.replaceAll(Caret, textWithSelection)
      val fromFileName = s"from-$uniqueToken-$index.$fromLangExtension"
      val toFileName = s"to-$uniqueToken-$index.scala"
      doTest(from, toModified, after, fromFileName, toFileName)
    }
  }

  protected def doTestToEmptyFile(fromText: String, expectedText: String): Unit = {
    doTest(fromText, Caret, expectedText)
  }
}