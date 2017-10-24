package org.jetbrains.plugins.scala.codeInsight.moveLeftRight

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.jetbrains.plugins.scala.util.TestUtils

/**
  * @author Nikolay.Tropin
  */
abstract class ScalaMoveLeftRightTestBase extends LightPlatformCodeInsightTestCase {
  protected def doTestFromLeftToRight(leftMostPosition: String, rightPositions: String*) {
    doTest(moveLeft = true, leftMostPosition)
    doTest(false, leftMostPosition, rightPositions: _*)
  }

  protected def doTestFromRightToLeft(rightMostPosition: String, leftPositions: String*) {
    doTest(false, rightMostPosition)
    doTest(true, rightMostPosition, leftPositions: _*)
  }

  private def doTest(moveLeft: Boolean, before: String, after: String*) {
    var current: String = before
    for (next <- after) {
      doTestSingle(moveLeft, current, next)
      current = next
    }
    doTestSingle(moveLeft, current, current)
    if (after.isEmpty) return
    for (i <- after.length - 2 to 0 by -1) {
      val prev: String = after(i)
      doTestSingle(!moveLeft, current, prev)
      current = prev
    }
    doTestSingle(!moveLeft, current, before)
  }

  private def doTestSingle(moveLeft: Boolean, before: String, after: String) {
    configureEditor(before)
    val actionId =
      if (moveLeft) IdeActions.MOVE_ELEMENT_LEFT
      else IdeActions.MOVE_ELEMENT_RIGHT
    LightPlatformCodeInsightTestCase.executeAction(actionId)
    checkResultByText(after)
  }

  private def configureEditor(fileText: String) {
    val fileName = s"${getTestName(false)}.scala"
    LightPlatformCodeInsightTestCase.configureFromFileText(fileName, fileText)
  }

  override def setUp(): Unit = {
    super.setUp()
    TestUtils.disableTimerThread()
  }
}
