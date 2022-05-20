package org.jetbrains.plugins.scala.lang.actions.editor

import com.intellij.codeInsight.completion.CompletionPhase
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.{EdtTestUtil, TestModeFlags}
import com.intellij.util.TimeoutUtil
import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.junit.Assert._

import scala.concurrent.duration.{Duration, DurationInt}

/** see [[com.intellij.codeInsight.completion.JavaCompletionAutoPopupTestCase]] */
class InterpolatedStringAutopopupTypingTest extends EditorActionTestBase {

  private val Dot = "."
  private val NegativeTestsTimeout = 5.seconds

  protected var myTester: CompletionAutoPopupTester = _

  override protected def setUp(): Unit = {
    super.setUp()
    myTester = new CompletionAutoPopupTester(myFixture)
    TestModeFlags.set[java.lang.Boolean](
      CompletionAutoPopupHandler.ourTestingAutopopup, true, getTestRootDisposable
    )
  }

  override protected def runInDispatchThread = false

  private def doTest(text: String): Unit = {
    myFixture.configureByText(defaultFileName, text)
    myTester.typeWithPauses(Dot)
    val lookupItems = myFixture.getLookupElementStrings
    assertTrue("Autopopup completion list must contain some injected reference methods", lookupItems.contains("charAt"))
  }

  private def doTestNoAutoCompletion(text: String): Unit = {
    myFixture.configureByText(defaultFileName, text)
    myFixture.`type`(Dot)
    val maybePhase = waitPhase(NegativeTestsTimeout) {
      case CompletionPhase.NoCompletion |
           _: CompletionPhase.CommittingDocuments |
           _: CompletionPhase.BgCalculation => false
      case _ => true
    }
    maybePhase match {
      case Some(stage) =>
        fail(s"No auto-completion is expected, but got completion stage: $stage")
      case _ =>
        assertTrue(Option(myFixture.getLookupElementStrings).forall(_.isEmpty))
    }
  }

  private def waitPhase(duration: Duration)(condition: CompletionPhase => Boolean): Option[CompletionPhase] = {
    val timeStart = System.currentTimeMillis()
    val maxTimeMs = duration.toMillis
    val sleepTimeMs = 10
    while(System.currentTimeMillis() - timeStart < maxTimeMs) {
      val phase = EdtTestUtil.runInEdtAndGet(() => CompletionServiceImpl.getCompletionPhase)
      if (condition(phase)) return Some(phase)
      TimeoutUtil.sleep(sleepTimeMs)
    }
    None
  }

  def testAutoPopupInInterpolatedString_Middle(): Unit = doTest {
    s"""val s: String = ""
       |val s1 = s"$$s$CARET something"
       |""".stripMargin
  }

  def testAutoPopupInInterpolatedString_End(): Unit = doTest {
    s"""val s: String = ""
       |val s1 = s"$$s$CARET"
       |""".stripMargin
  }

  def testAutoPopupInInterpolatedString_Middle_Negative_NotRightAfterReference(): Unit = doTestNoAutoCompletion {
    s"""val s: String = ""
       |val s1 = s"$$s $CARET something"
       |""".stripMargin
  }

  def testAutoPopupInInterpolatedString_End_Negative_NotRightAfterReference(): Unit = doTestNoAutoCompletion {
    s"""val s: String = ""
       |val s1 = s"$$s $CARET"
       |""".stripMargin
  }

  def testAutoPopupInInterpolatedString_End_Negative_BeforeReference(): Unit = doTestNoAutoCompletion {
    s"""val s: String = ""
       |val s1 = s"$CARET$$s"
       |""".stripMargin
  }

  /**
   * Multiline Strings
   */

  def testAutoPopupInMultilineInterpolatedString_Middle(): Unit = doTest {
    s"""val s: String = ""
       |val s1 = s$qqq$$s$CARET something$qqq
       |""".stripMargin
  }

  def testAutoPopupInMultilineInterpolatedString_End(): Unit = doTest {
    s"""val s: String = ""
       |val s1 = s$qqq$$s$CARET$qqq
       |""".stripMargin
  }

  def testAutoPopupInMultilineInterpolatedString_MultipleLines_Middle(): Unit = doTest {
    s"""val s: String = ""
       |val s1 = s$qqq
       |             |$$s$CARET something
       |             |$qqq
       |""".stripMargin
  }

  def testAutoPopupInMultilineInterpolatedString_MultipleLines_LineEnd(): Unit = doTest {
    s"""val s: String = ""
       |val s1 = s$qqq
       |             |$$s$CARET
       |             |something
       |             |$qqq
       |""".stripMargin
  }

  def testAutoPopupInMultilineInterpolatedString_MultipleLines_End(): Unit = doTest {
    s"""val s: String = ""
       |val s1 = s${qqq}something
       |             |$$s$CARET$qqq
       |""".stripMargin
  }

  def testAutoPopupInMultilineInterpolatedString_MultipleLines_Negative_NotInsideString(): Unit = doTestNoAutoCompletion {
    s"""val s: String = ""
       |val s1 = s$q$CARET${qq}something
       |             |$$s$qqq
       |""".stripMargin
  }

//  // the test flickeres, don't know why
//  def testAutoPopupInMultilineInterpolatedString_MultipleLines_Negative_NotInsideString_1(): Unit = doNegativeTest {
//    s"""val s: String = ""
//       |val s1 = s$qq$CARET${q}something
//       |             |$$s$qqq
//       |""".stripMargin
//  }

  def testAutoPopupInMultilineInterpolatedString_MultipleLines_Negative_NotInsideString_2(): Unit = doTestNoAutoCompletion {
    s"""val s: String = ""
       |val s1 = s${qqq}something
       |             |$$s$q$CARET$qq
       |""".stripMargin
  }

  def testAutoPopupInMultilineInterpolatedString_MultipleLines_Negative_NotInsideString_3(): Unit = doTestNoAutoCompletion {
    s"""val s: String = ""
       |val s1 = s${qqq}something
       |             |$$s$qq$CARET$q
       |""".stripMargin
  }
}
