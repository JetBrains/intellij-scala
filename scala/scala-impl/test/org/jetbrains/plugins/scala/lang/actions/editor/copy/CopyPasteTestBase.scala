package org.jetbrains.plugins.scala.lang.actions.editor.copy

import com.intellij.lang.ASTNode
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.{MarkersUtils, TypeAnnotationSettings}
import org.junit.Assert.assertTrue

import java.awt.datatransfer.StringSelection
import scala.collection.mutable.ArrayBuffer

abstract class CopyPasteTestBase extends ScalaLightCodeInsightFixtureTestCase {
  protected val Start = EditorTestUtil.SELECTION_START_TAG
  protected val End = EditorTestUtil.SELECTION_END_TAG
  protected val Caret = EditorTestUtil.CARET_TAG

  protected val tab = "\t"
  protected val empty = ""

  def fromLangExtension: String = "scala"

  override protected def sharedProjectToken: SharedTestProjectToken =
    SharedTestProjectToken.ByTestClassAndScalaSdkAndProjectLibraries(this)

  private var oldSettings: ScalaCodeStyleSettings = _
  private var oldBlankLineSetting: Int = _

  //keeping hard refs to AST nodes to avoid flaky tests (as a workaround for SCL-20527 (see solution proposals))
  protected val myASTHardRefs: ArrayBuffer[ASTNode] = ArrayBuffer.empty

  override protected def setUp(): Unit = {
    super.setUp()

    val project = getProject
    oldSettings = ScalaCodeStyleSettings.getInstance(project)
    oldBlankLineSetting = oldSettings.BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES
    oldSettings.BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES = 0
    TypeAnnotationSettings.set(project, TypeAnnotationSettings.alwaysAddType(oldSettings))
  }

  override def tearDown(): Unit = {
    myASTHardRefs.clear()
    val project = getProject
    ScalaCodeStyleSettings.getInstance(project).BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES = oldBlankLineSetting
    TypeAnnotationSettings.set(project, oldSettings)
    super.tearDown()
  }

  protected final def doTest(from: String, to: String, after: String): Unit = {
    doTest(from, to, after, s"from.$fromLangExtension", "to.scala")
  }

  //NOTE: in IntelliJ Platform tests for copy/paste
  //they use IdeActions.ACTION_EDITOR_COPY/ACTION_EDITOR_PASTE
  //instead of IdeActions.ACTION_COPY & IdeActions.ACTION_PASTE
  //I am not sure what is the difference though
  protected def doTest(from: String, to: String, after: String, fromFileName: String, toFileName: String): Unit = {
    def normalize(s: String): String = s.replace("\r", "")

    val containsCaretOrSelection = to.contains(Caret) || to.contains(Start) && to.contains(End)
    assertTrue("Content of target file doesn't contain caret marker or selection markers", containsCaretOrSelection)

    val fileFrom = myFixture.configureByText(fromFileName, normalize(from))
    myASTHardRefs += fileFrom.getNode
    myFixture.performEditorAction(IdeActions.ACTION_COPY)

    val fileTo = myFixture.configureByText(toFileName, normalize(to))
    myASTHardRefs += fileTo.getNode
    myFixture.performEditorAction(IdeActions.ACTION_PASTE)

    myFixture.checkResult(normalize(after), true)
  }

  protected def doPasteTest(pastedText: String, to: String, after: String): Unit = {
    val containsCaretOrSelection = to.contains(Caret) || to.contains(Start) && to.contains(End)
    assertTrue("Content of target file doesn't contain caret marker or selection markers", containsCaretOrSelection)

    val copyPasteManager = CopyPasteManager.getInstance
    copyPasteManager.setContents(new StringSelection(pastedText))

    val fileTo = myFixture.configureByText(s"to.scala", to.withNormalizedSeparator)
    myASTHardRefs += fileTo.getNode
    myFixture.performEditorAction(IdeActions.ACTION_PASTE)

    myFixture.checkResult(after.withNormalizedSeparator, true)
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
      //NOTE: after SCL-21664, assumptions mentioned in ScalaDoc of parent method is not the same
      //Content after the caret can change the semantics of surrounding code
//      s"""$Caret${Start}print("Existing code 1")$End""",
//      s"""$Caret$Start
//         |  print("Existing code 2")$tab$empty
//         | $End""".stripMargin,
    )

    val uniqueToken = System.currentTimeMillis
    for (case (textWithSelection, index) <- textsWithSelections.zipWithIndex) {
      val toModified = to.replaceAll(Caret, textWithSelection)
      val fromFileName = s"from-$uniqueToken-$index.$fromLangExtension"
      val toFileName = s"to-$uniqueToken-$index.scala"
      try doTest(from, toModified, after, fromFileName, toFileName) catch {
        case error: AssertionError =>
          System.err.println(s"Selection $index: $textWithSelection")
          throw error
      }
    }
  }

  protected def doTestToEmptyFile(fromText: String, expectedText: String): Unit = {
    doTest(fromText, Caret, expectedText)
  }

  protected def doTestCutAndPasteDoesNotBreakTheCode(code: String): Unit = {
    myFixture.configureByText("dummy.scala", code)
    myFixture.performEditorAction(IdeActions.ACTION_CUT)
    myFixture.performEditorAction(IdeActions.ACTION_PASTE)

    val (codeWithoutSelection, _) = MarkersUtils.extractMarkers(code, Seq((START, END)), Some(CARET))
    myFixture.checkResult(codeWithoutSelection, true)
  }
}