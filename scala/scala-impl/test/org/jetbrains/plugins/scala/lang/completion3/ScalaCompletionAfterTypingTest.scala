package org.jetbrains.plugins.scala.lang.completion3

class ScalaCompletionAfterTypingTest extends ScalaCodeInsightTestBase {
  private var myTextToType: String = ""

  override protected def changePsiAt(offset: Int): Unit = {
    getEditor.getCaretModel.moveToOffset(offset)
    myTextToType.foreach { char =>
      myFixture.`type`(char)
      commitDocumentInEditor()
      myFixture.completeBasic()
    }
  }

  //SCL-17313
  def testNoItemsFromPreviousRef(): Unit = {

    myTextToType = "B."

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
}
