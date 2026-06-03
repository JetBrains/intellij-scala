package org.jetbrains.plugins.scala.lang.autoImport

class AddImportsBelowDirectivesTest extends ScalaImportTypeFixTestBase {

  private val Directive = "//> using dep foo"
  private val ExprThatNeedsImport = "Random.nextBoolean()"
  private val ImportName = "java.util.Random"

  def test_directive_followed_by_statement(): Unit = {
    val code =
      s"""$Directive
         |val x = $CARET$ExprThatNeedsImport
         |""".stripMargin

    val expected =
      s"""$Directive
        |
        |import $ImportName
        |
        |val x = $ExprThatNeedsImport
        |""".stripMargin

    doTest(code, expected, ImportName)
  }

  def test_directive_followed_by_comment_and_statement(): Unit = {
    val code =
      s"""$Directive
         |// comment
         |val x = $CARET$ExprThatNeedsImport
         |""".stripMargin

    val expected =
      s"""$Directive
        |
        |import $ImportName
        |
        |// comment
        |val x = $ExprThatNeedsImport
        |""".stripMargin

    doTest(code, expected, ImportName)
  }

  def test_directive_followed_by_comment_and_statement_and_directive(): Unit = {
    val code =
      s"""$Directive
         |// comment
         |$Directive
         |val x = $CARET$ExprThatNeedsImport
         |""".stripMargin

    val expected =
      s"""$Directive
        |// comment
        |$Directive
        |
        |import $ImportName
        |
        |val x = $ExprThatNeedsImport
        |""".stripMargin

    doTest(code, expected, ImportName)
  }

  def test_directive_followed_by_blockcomment_and_statement(): Unit = {
    val code =
      s"""$Directive
         |/* comment */
         |val x = $CARET$ExprThatNeedsImport
         |""".stripMargin

    val expected =
      s"""$Directive
        |
        |import $ImportName
        |
        |/* comment */
        |val x = $ExprThatNeedsImport
        |""".stripMargin

    doTest(code, expected, ImportName)
  }

  def test_directive_followed_by_blockcomment_and_statement_and_directive(): Unit = {
    val code =
      s"""$Directive
         |/* comment */
         |$Directive
         |val x = $CARET$ExprThatNeedsImport
         |""".stripMargin

    val expected =
      s"""$Directive
        |/* comment */
        |$Directive
        |
        |import $ImportName
        |
        |val x = $ExprThatNeedsImport
        |""".stripMargin

    doTest(code, expected, ImportName)
  }

  def test_directive_followed_by_scaladoc_and_statement(): Unit = {
    val code =
      s"""$Directive
         |/** ScalaDoc */
         |val x = $CARET$ExprThatNeedsImport
         |""".stripMargin

    val expected =
      s"""$Directive
        |
        |import $ImportName
        |
        |/** ScalaDoc */
        |val x = $ExprThatNeedsImport
        |""".stripMargin

    doTest(code, expected, ImportName)
  }

  def test_directive_followed_by_scaladoc_and_statement_and_directive(): Unit = {
    val code =
      s"""$Directive
         |/** ScalaDoc */
         |$Directive
         |val x = $CARET$ExprThatNeedsImport
         |""".stripMargin

    val expected =
      s"""$Directive
        |/** ScalaDoc */
        |$Directive
        |
        |import $ImportName
        |
        |val x = $ExprThatNeedsImport
        |""".stripMargin

    doTest(code, expected, ImportName)
  }

  def test_directive_followed_by_comment_statement_comment_statement(): Unit = {
    val code =
      s"""$Directive
         |// comment
         |println("")
         |// comment
         |println("")
         |val x = $CARET$ExprThatNeedsImport
         |""".stripMargin

    val expected =
      s"""$Directive
         |
         |import $ImportName
         |// comment
         |println("")
         |// comment
         |println("")
         |val x = $ExprThatNeedsImport
         |""".stripMargin

    doTest(code, expected, ImportName)
  }
}
