package org.jetbrains.plugins.scala.lang.actions.editor.copy

class ScaladocCopyPastePreProcessorTest extends CopyPasteTestBase {

  def testPasteMultilineContentToScaladoc_CaretAfterDescriptionLine(): Unit = {
    doPasteTest(
      "1\n2\n3",
      s"""object Outer {
         |  /**
         |   * description $CARET
         |   */
         |  class A
         |}""".stripMargin,
      s"""object Outer {
         |  /**
         |   * description 1
         |   * 2
         |   * 3$CARET
         |   */
         |  class A
         |}""".stripMargin,
    )
  }

  def testPasteMultilineContentToScaladoc_CaretAfterAsterisk(): Unit = {
    doPasteTest(
      "1\n2\n3",
      s"""object Outer {
         |  /**
         |   * description
         |   * $CARET
         |   */
         |  class A
         |}""".stripMargin,
      s"""object Outer {
         |  /**
         |   * description
         |   * 1
         |   * 2
         |   * 3$CARET
         |   */
         |  class A
         |}""".stripMargin,
    )
  }

  def testPasteMultilineContentToScaladoc_CaretAtBlankLine(): Unit = {
    doPasteTest(
      "1\n2\n3",
      s"""object Outer {
         |  /**
         | $CARET
         |   */
         |  class A
         |}""".stripMargin,
      s"""object Outer {
         |  /**
         |   1
         |   2
         |   3$CARET
         |   */
         |  class A
         |}""".stripMargin,
    )
  }

  def testPasteMultilineContentToScaladoc_CaretAtBlankLine_AfterDescription(): Unit = {
    doPasteTest(
      "1\n2\n3",
      s"""object Outer {
         |  /**
         |   * description
         | $CARET
         |   */
         |  class A
         |}""".stripMargin,
      s"""object Outer {
         |  /**
         |   * description
         |   1
         |   2
         |   3$CARET
         |   */
         |  class A
         |}""".stripMargin,
    )
  }

  def testPasteMultilineContentToScaladoc_CaretAtBlankLine_AfterDescription_AndAnotherBlankLine(): Unit = {
    doPasteTest(
      "1\n2\n3",
      s"""object Outer {
         |  /**
         |   * description
         |
         | $CARET
         |   */
         |  class A
         |}""".stripMargin,
      s"""object Outer {
         |  /**
         |   * description
         |
         |   1
         |   2
         |   3$CARET
         |   */
         |  class A
         |}""".stripMargin,
    )
  }

  def testPasteMultilineContentToScaladoc_CaretAfterAsterisk_ScalaStyleAsterisk(): Unit = {
    getScalaCodeStyleSettings.USE_SCALADOC2_FORMATTING = true

    doPasteTest(
      "1\n2\n3",
      s"""object Outer {
         |  /**
         |    * description
         |    * $CARET
         |    */
         |  class A
         |}""".stripMargin,
      s"""object Outer {
         |  /**
         |    * description
         |    * 1
         |    * 2
         |    * 3$CARET
         |    */
         |  class A
         |}""".stripMargin,
    )
  }

  def testPasteMultilineContentToScaladoc_CaretAtBlankLine_ScalaStyleAsterisk(): Unit = {
    getScalaCodeStyleSettings.USE_SCALADOC2_FORMATTING = true

    doPasteTest(
      "1\n2\n3",
      s"""object Outer {
         |  /**
         |    * description
         | $CARET
         |    */
         |  class A
         |}""".stripMargin,
      s"""object Outer {
         |  /**
         |    * description
         |    1
         |    2
         |    3$CARET
         |    */
         |  class A
         |}""".stripMargin,
    )
  }
}