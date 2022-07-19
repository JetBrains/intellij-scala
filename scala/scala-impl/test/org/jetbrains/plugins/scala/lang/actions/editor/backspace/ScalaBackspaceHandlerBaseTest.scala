package org.jetbrains.plugins.scala.lang.actions.editor.backspace

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.extensions.{StringExt => StringExt1}
import org.jetbrains.plugins.scala.lang.actions.editor.backspace.ScalaBackspaceHandlerBaseTest.StringExt
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.Scala3TestDataBracelessCode
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.Scala3TestDataBracelessCode.{CodeWithDebugName, WrapperCodeContexts, injectCodeWithIndentAdjust}

//marker trait for better tests discoverability of the original class
trait ScalaBackspaceHandlerTestLike

abstract class ScalaBackspaceHandlerBaseTest extends EditorActionTestBase with ScalaBackspaceHandlerTestLike {

  protected def doTest(before: String, after: String): Unit = {
    doBackspaceTest(before, after)
  }

  protected def doBackspaceTest(before: String, after: String): Unit = {
    performTest(before, after, stripTrailingSpacesAfterAction = true) { () =>
      performBackspaceAction()
    }
  }

  /**
   * The marker indicates that the caret is located at position which is followed by a single space (' ')<br>
   * It's convenient to use it instead of just ${CARET} not to increase visual line length in test data
   */
  protected val SpaceCaretMarker = '#'

  /**
   * Contexts in which Backspace will jump to the previous line and will not do unindent
   * when caret is placed in the left-most position of the injected code fragment.
   *
   * Example of such wrapper context {{{
   *   class A {
   *     class B {
   *       <INJECTED_CODE_MARKER>
   *     }
   *   }
   * }}}
   * Note, that if we place caret at the INJECTED_CODE_MARKER and press Backspace, we will jump to the previous line
   */
  protected val WrapperContextsWithJumpToPreviousLine: Seq[CodeWithDebugName] = {
    import Scala3TestDataBracelessCode.WrapperCodeContexts._
    AllContexts_TopLevel ++ AllContexts_WithBracesOrEndMarkerAtDeepestPosition
  }

  protected def doSequentialBackspaceTest_InAllWrapperContexts(textWithCaretMarkers: String): Unit =
    doSequentialBackspaceTest_InContexts(WrapperCodeContexts.AllContexts)(textWithCaretMarkers)

  protected def doSequentialBackspaceTest_InContexts(wrapperContexts: Seq[CodeWithDebugName])(textWithCaretMarkers: String): Unit =
    injectCodeInContexts(textWithCaretMarkers, wrapperContexts).foreach(doSequentialBackspaceTest)

  protected def injectCodeInContexts(code: String, wrapperContexts: Seq[CodeWithDebugName]): Iterator[String] =
    wrapperContexts.iterator.map(wrapper => injectCodeWithIndentAdjust(code, wrapper.code))

  protected def doSequentialBackspaceTest(
    textWithCaretMarkers0: String
  ): Unit = {
    val beforeAndAfterStates = prepareBeforeAfterStates(textWithCaretMarkers0)
    beforeAndAfterStates.sliding(2).foreach { case Seq(before, after) =>
      doBackspaceTest(before, after)
    }
  }

  protected def prepareBeforeAfterStates(textWithCaretMarkers0: String): Seq[String] = {
    val textWithCaretMarkers = textWithCaretMarkers0.withNormalizedSeparator

    val caretIndexes = textWithCaretMarkers.zipWithIndex
      .filter(_._1 == SpaceCaretMarker)
      .map(_._2)
      .reverse // reverse, because the first caret in the text actually represents the state after the last backspace action

    val firstCaretIdx = caretIndexes.head
    val textClean = textWithCaretMarkers.replace(SpaceCaretMarker, ' ')

    val textWithCaretStates: Seq[(String, Int)] =
      caretIndexes.foldLeft(Vector.empty[(String, Int)]) {
        case (acc, caretIdx) =>
          val textNext = textClean.removeRange(caretIdx, firstCaretIdx)
          acc :+ (textNext, caretIdx)
      }

    textWithCaretStates.map { case (text, caretIdx) =>
      text.withCaretMarker(caretIdx)
    }
  }
}


object ScalaBackspaceHandlerBaseTest {

  implicit class StringExt(private val str: String) extends AnyVal {
    def removeRange(beginIndex: Int, endIndex: Int): String =
      replaceRange(beginIndex, endIndex, "")

    def replaceRange(beginIndex: Int, endIndex: Int, replacement: String): String =
      if (beginIndex == endIndex)
        str
      else {
        val start = str.substring(0, beginIndex)
        val end = str.substring(endIndex, str.length)
        if (replacement.isEmpty) start + end
        else start + replacement + end
      }

    def withCaretMarker(idx: Int): String =
      str.replaceRange(idx, idx + 1, EditorTestUtil.CARET_TAG)
  }
}