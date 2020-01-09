package org.jetbrains.plugins.scala.lang.actions.editor

import org.jetbrains.plugins.scala.base.EditorActionTestBase

abstract class EditorTypeActionTestBase extends EditorActionTestBase {

  protected def typedChar: Char

  protected def doTest(before: String, after: String, fileName: String = defaultFileName): Unit =
    checkGeneratedTextAfterTyping(before, after, typedChar)

  protected  def doTestWithEmptyLastLine(before: String, after: String, fileName: String = defaultFileName): Unit = {
    doTest(before, after)
    doTest(before + "\n", after + "\n")
  }

  protected  def doRepetitiveTypingTest(typingSteps: String*): Unit =
    typingSteps.sliding(2).foreach { case Seq(before, after) =>
      doTestWithEmptyLastLine(before, after)
    }
}
