package org.jetbrains.plugins.scala.lang.actions.editor.backspace

import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.Scala3TestDataBracelessCode.{CodeWithDebugName, WrapperCodeContexts}

abstract class ScalaBackspaceAndEnterHandlerBaseTest extends ScalaBackspaceHandlerBaseTest {

  protected def doEnterTest(before: String, after: String): Unit = {
    performTest(before, after, stripTrailingSpacesAfterAction = true) { () =>
      performEnterAction()
    }
  }

  protected def doSequentialBackspaceAndEnterTest_InAllWrapperContexts(stepsToDropFromTailForEnterAction: Int, textWithCaretMarkers: String): Unit =
    doSequentialBackspaceAndEnterTest_InContexts(WrapperCodeContexts.AllContexts)(stepsToDropFromTailForEnterAction, textWithCaretMarkers)

  protected def doSequentialBackspaceAndEnterTest_InContexts(wrapperContexts: Seq[CodeWithDebugName])(stepsToDropFromTailForEnterAction: Int, textWithCaretMarkers: String): Unit =
    injectCodeInContexts(textWithCaretMarkers, wrapperContexts).foreach(doSequentialBackspaceAndEnterTest(_, stepsToDropFromTailForEnterAction))

  /**
   * First sequence of backspace actions will be tested.
   * Then sequence of enter actions in reverse order will be tested.
   *
   * @param stepsToDropFromTailForEnterAction number of code states to skip (from the end) when testing Enter action
   *                                          this is required when the first code state for Backspace action is
   *                                          located in some poorly-indented state, e.g. in this example
   *                                          stepsToDropFromTailForEnterAction = 1
   *                                          {{{
   *                                            def foo = {
   *                                              #
   *                                              #   #
   *                                            }
   *                                          }}}
   */
  protected def doSequentialBackspaceAndEnterTest(
    textWithCaretMarkers0: String,
    stepsToDropFromTailForEnterAction: Int
  ): Unit = {
    val statesForBackspace = prepareBeforeAfterStates(textWithCaretMarkers0)
    statesForBackspace.sliding(2).foreach { case Seq(before, after) =>
      doBackspaceTest(before, after)
    }

    val statesForEnter = statesForBackspace.drop(stepsToDropFromTailForEnterAction).reverse
    statesForEnter.sliding(2).foreach { case Seq(before, after) =>
      doEnterTest(before, after)
    }
  }
}
