package org.jetbrains.plugins.scala.codeInspection.unused.positive

import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedDeclarationInspectionTestBase

class Scala2UnusedGlobalDeclarationInspectionTest extends ScalaUnusedDeclarationInspectionTestBase {
  private def addFile(text: String): Unit = myFixture.addFileToProject("Foo.scala", text)

  def test_auxiliary_constructors(): Unit = {
    addFile(
      """
        | object UnusedConstructor {
        |   val foo = new Foo()
        | }
        |"""
        .stripMargin)
    checkTextHasError(
      """
        |  import scala.annotation.unused
        |  @unused class Foo(@unused foo: String, @unused n: Int) {
        |    def this() = this("foo", 0)
        |    def this(str: String) = this(str, 0)
        |  }
        |""".stripMargin, allowAdditionalHighlights = true)
  }

  def test_overloaded_methods(): Unit = {
    addFile(
      """
        | object UnusedConstructor {
        |   val foo = new Foo()
        |   foo.aaa()
        | }
        |"""
        .stripMargin)
    checkTextHasError(
      """
        |  import scala.annotation.unused
        |  @unused class Foo{
        |    def aaa(): Unit = {}
        |    def aaa(@unused str: String): Unit = {}
        |  }
        |""".stripMargin, allowAdditionalHighlights = true)
  }

  private def testOperatorNotUsed(operatorName: String): Unit = {
    checkTextHasError(
      s"""
         | class Num(n: Int) {
         |   def $operatorName(n: Int): Num = new Num(n + this.n)
         | }
         |""".stripMargin, allowAdditionalHighlights = true)
  }

  def test_plus_not_used(): Unit = testOperatorNotUsed("+")
  def test_minus_not_used(): Unit = testOperatorNotUsed("-")
  def test_tilde_not_used(): Unit = testOperatorNotUsed("~")
  def test_eqeq_not_used(): Unit = testOperatorNotUsed("==")
  def test_less_not_used(): Unit = testOperatorNotUsed("<")
  def test_lesseq_not_used(): Unit = testOperatorNotUsed("<=")
  def test_greater_not_used(): Unit = testOperatorNotUsed(">")
  def test_greatereq_not_used(): Unit = testOperatorNotUsed(">=")
  def test_bang_not_used(): Unit = testOperatorNotUsed("!")
  def test_percent_not_used(): Unit = testOperatorNotUsed("%")
  def test_up_not_used(): Unit = testOperatorNotUsed("^")
  def test_amp_not_used(): Unit = testOperatorNotUsed("&")
  def test_bar_not_used(): Unit = testOperatorNotUsed("|")
  def test_times_not_used(): Unit = testOperatorNotUsed("*")
  def test_div_not_used(): Unit = testOperatorNotUsed("/")
  def test_bslash_not_used(): Unit = testOperatorNotUsed("\\")
  def test_qmark_not_used(): Unit = testOperatorNotUsed("?")
}
