package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase

class ScalaCompletionAfterTypingTest extends ScalaCompletionTestBase {
  private def setCustomTyper(myTextToType: String): Unit = {
    scalaCompletionTestFixture.setCustomBeforeCompletionListener(() => {
      getEditor.getCaretModel.moveToOffset(myFixture.getEditor.getCaretModel.getOffset)
      myTextToType.foreach { char =>
        myFixture.`type`(char)
        scalaCompletionTestFixture.commitDocumentInEditor()
        myFixture.completeBasic()
      }
    })
  }

  //SCL-17313
  def testNoItemsFromPreviousRef(): Unit = {
    setCustomTyper("B.")

    doCompletionTest(
      fileText =
        s"""
           |object AAA {
           |  object BBB {
           |    object CCC
           |  }
           |
           |  AAA.BB$CARET
           |}""".stripMargin,
      resultText =
        """
          |object AAA {
          |  object BBB {
          |    object CCC
          |  }
          |
          |  AAA.BBB.CCC
          |}""".stripMargin,
      item = "CCC"
    )
  }

  def testCompletionBeforeLastRefInBlock(): Unit = {
    setCustomTyper(".")

    doCompletionTest(
      fileText =
        s"""
          |object Test {
          |  def foo: String = {
          |    val t: String = ???
          |    t$CARET
          |    ???
          |  }
          |}""".stripMargin,

      resultText =
        s"""object Test {
           |  def foo: String = {
           |    val t: String = ???
           |    t.length
           |    ???
           |  }
           |}""".stripMargin,

      item = "length"
    )
  }
}
