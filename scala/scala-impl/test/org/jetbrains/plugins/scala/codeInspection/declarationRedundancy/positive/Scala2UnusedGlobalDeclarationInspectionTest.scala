package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class Scala2UnusedGlobalDeclarationInspectionTest extends ScalaUnusedDeclarationInspectionTestBase {
  private def addFile(text: String): Unit = myFixture.addFileToProject("Foo.scala", text)

  private def testOperator(operatorName: String): Unit = {
    checkTextHasError(
      s"""
         | class Num(n: Int) {
         |   def $START$operatorName$END(n: Int): Num = new Num(n + this.n)
         | }
         |""".stripMargin)
  }

  def test_plus(): Unit = testOperator("+")

  def test_minus(): Unit = testOperator("-")

  def test_tilde(): Unit = testOperator("~")

  def test_eqeq(): Unit = testOperator("==")

  def test_less(): Unit = testOperator("<")

  def test_lesseq(): Unit = testOperator("<=")

  def test_greater(): Unit = testOperator(">")

  def test_greatereq(): Unit = testOperator(">=")

  def test_bang(): Unit = testOperator("!")

  def test_percent(): Unit = testOperator("%")

  def test_up(): Unit = testOperator("^")

  def test_amp(): Unit = testOperator("&")

  def test_bar(): Unit = testOperator("|")

  def test_times(): Unit = testOperator("*")

  def test_div(): Unit = testOperator("/")

  def test_bslash(): Unit = testOperator("\\")

  def test_qmark(): Unit = testOperator("?")

  def test_presence_in_string_literal(): Unit = {
    addFile("\"Abc\"")
    checkTextHasError(s"trait ${START}Abc$END")
  }

  def test_presence_in_string_literal_and_comment(): Unit = {
    addFile(
      """@scala.annotation.unused
        |class SomeOtherClass {
        |  "SomeName"
        |
        |  //SomeName
        |  /*SomeName*/
        |  /**SomeName*/
        |}
        |""".stripMargin)
    checkTextHasError(
      s"""@scala.annotation.unused
        |class MainClass {
        |  class ${START}SomeName$END
        |}
        |""".stripMargin)
  }

  def test_reference_pattern_with_same_name(): Unit = {
    addFile("object Ctx { val Abc = 42 }")
    checkTextHasError(s"trait ${START}Abc$END")
  }

  def test_other_type_definition_with_same_name(): Unit = {
    addFile("object Ctx1 { trait Abc }")
    checkTextHasError(s"@scala.annotation.unused object Ctx2 { trait ${START}Abc$END }")
  }
}
