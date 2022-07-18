package org.jetbrains.plugins.scala
package lang.actions.editor

import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.junit.experimental.categories.Category

@Category(Array(classOf[LanguageTests]))
abstract class EditorTypeActionTestBase extends EditorActionTestBase {

  protected def typedChar: Char

  protected def doTest(before: String, after: String, fileName: String = defaultFileName): Unit =
    checkGeneratedTextAfterTyping(before, after, typedChar, fileName)

  protected def doTestWithEmptyLastLine(before0: String, after0: String): Unit = {
    val before = before0.stripTrailing()
    val after = after0.stripTrailing()
    doTest(before, after)
    doTest(before + "\n", after + "\n") // test edge case when there is a new line after the code
    doTest(before + "  \n", after + "  \n") // test edge case when there is spaces and new line after the code
  }

  protected  def doRepetitiveTypingTest(typingSteps: String*): Unit =
    typingSteps.sliding(2).foreach { case Seq(before, after) =>
      doTestWithEmptyLastLine(before, after)
    }
}
