package org.jetbrains.plugins.scala
package lang
package completeStatement

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * User: Dmitry.Naydanov
  * Date: 27.07.15.
  */
abstract class ScalaCompleteStatementTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaLightCodeInsightFixtureTestAdapter._

  protected val fileType: LanguageFileType = ScalaFileType.INSTANCE

  protected override def setUp(): Unit = {
    super.setUp()

    StatisticsManager.getInstance match {
      case impl: StatisticsManagerImpl => impl.enableStatistics(getTestRootDisposable)
    }

    //We should change this setting in order to be sure EnterProcessor works without 'swap-settings-hack'
    //it was in org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor#moveCaretInsideBracesIfAny
    getCurrentCodeStyleSettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true
  }

  override protected def tearDown(): Unit = {
    getCurrentCodeStyleSettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false

    super.tearDown()
  }

  def doCompletionTest(fileText: String, resultText: String): Unit = {
    val fixture = getFixture

    fixture.configureByText(fileType, normalize(fileText))
    fixture.performEditorAction(ACTION_EDITOR_COMPLETE_STATEMENT)
    fixture.checkResult(normalize(resultText), /*stripTrailingSpaces = */ true)
  }
}

class JavaCompleteStatementTest extends ScalaCompleteStatementTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  override protected val fileType: LanguageFileType = JavaFileType.INSTANCE

  def testFormatJava(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |    int d=7+7+7+77;$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |    int d = 7 + 7 + 7 + 77;$CARET
         |}
      """.stripMargin
  )

  def testIfConditionJava(): Unit = doCompletionTest( //WHAT THE _?!
    fileText =
      s"""
         |class B {
         |    public static void main(String[] args) {
         |        if $CARET
         |    }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |    public static void main(String[] args) {
         |        if ($CARET) {
         |        }
         |    }
         |}
      """.stripMargin
  )

  def testIfCondition2Java(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |    public static void main(String[] args) {
         |        if ()$CARET
         |    }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |    public static void main(String[] args) {
         |        if ($CARET) {
         |        }
         |    }
         |}
      """.stripMargin
  )
}
