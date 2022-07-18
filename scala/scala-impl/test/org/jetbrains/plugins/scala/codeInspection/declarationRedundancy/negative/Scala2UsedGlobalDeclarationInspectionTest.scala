package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class Scala2UsedGlobalDeclarationInspectionTest extends ScalaUnusedDeclarationInspectionTestBase {

  private def addScalaFile(text: String, name: String = "Foo"): Unit = myFixture.addFileToProject(s"$name.scala", text)

  private def addJavaFile(text: String, name: String = "Foo"): Unit = myFixture.addFileToProject(s"$name.java", text)

  def test_trait_extends_trait(): Unit = {
    addScalaFile("trait Foo extends Bar")
    checkTextHasNoErrors("trait Bar")
  }

  def test_class_extends_trait(): Unit = {
    addScalaFile("class Foo extends Bar")
    checkTextHasNoErrors("trait Bar")
  }

  def test_trait_extends_class(): Unit = {
    addScalaFile("trait Foo extends Bar")
    checkTextHasNoErrors("class Bar")
  }

  def test_class_extends_class(): Unit = {
    addScalaFile("class Foo extends Bar")
    checkTextHasNoErrors("class Bar")
  }

  def test_object_extends_trait(): Unit = {
    addScalaFile("object Foo extends Bar")
    checkTextHasNoErrors("trait Bar")
  }

  def test_object_extends_class(): Unit = {
    addScalaFile("object Foo extends Bar")
    checkTextHasNoErrors("class Bar")
  }

  def test_public_def(): Unit = {
    addScalaFile("new Bar().fizz")
    checkTextHasNoErrors("class Bar { def fizz = 42 }")
  }

  def test_public_var(): Unit = {
    addScalaFile("new Bar().fizz")
    checkTextHasNoErrors("class Bar { var fizz = 42 }")
  }

  def test_public_val(): Unit = {
    addScalaFile("new Bar().fizz")
    checkTextHasNoErrors("class Bar { val fizz = 42 }")
  }

  def test_case_class_public_field_when_extracted_into_a_different_name(): Unit = {
    addScalaFile("Bar(42) match { case Bar(extracted) => extracted }")
    checkTextHasNoErrors("case class Bar(fizz: Int)")
  }

  def test_case_class_private_field_when_extracted_into_a_different_name(): Unit = {
    addScalaFile("Bar(42) match { case Bar(extracted) => extracted }")
    checkTextHasNoErrors("case class Bar(private val fizz: Int)")
  }

  def test_implicit_class(): Unit = {
    addScalaFile("import Foo.Bar; 0.plus42")
    checkTextHasNoErrors("object Foo { implicit class Bar(x: Int) { def plus42 = x + 42 } }")
  }

  def test_auxiliary_constructors(): Unit = {
    addScalaFile(
      """
        | object UnusedConstructor {
        |   val foo1 = new Foo()
        |   val foo2 = new Foo("foo")
        | }
        |"""
        .stripMargin)
    checkTextHasNoErrors(
      """
        |  import scala.annotation.unused
        |  @unused class Foo(@unused foo: String, @unused n: Int) {
        |    def this() = this("foo", 0)
        |    def this(str: String) = this(str, 0)
        |  }
        |""".stripMargin)
  }

  def test_overloaded_methods(): Unit = {
    addScalaFile(
      """
        | object UnusedConstructor {
        |   val foo = new Foo()
        |   foo.aaa()
        |   foo.aaa("foo")
        | }
        |"""
        .stripMargin)
    checkTextHasNoErrors(
      """
        |  import scala.annotation.unused
        |  @unused class Foo{
        |    def aaa(): Unit = {}
        |    def aaa(@unused str: String): Unit = {}
        |  }
        |""".stripMargin)
  }

  private def testOperatorUsedFromJava(operatorName: String, javaMethodName: String): Unit = {
    addJavaFile(
      s"""
         |public class UsedOperatorJava {
         |     public static void main(String[] args) {
         |         new Num(1).$javaMethodName(1);
         |     }
         |"""
        .stripMargin, "UsedOperatorJava")
    checkTextHasNoErrors(
      s"""
         | class Num(n: Int) {
         |   def $operatorName(n: Int): Num = new Num(n + this.n)
         | }
         |""".stripMargin)
  }

  def test_plus_used_from_java(): Unit = testOperatorUsedFromJava("+", "$plus")

  def test_minus_used_from_java(): Unit = testOperatorUsedFromJava("-", "$minus")

  def test_tilde_used_from_java(): Unit = testOperatorUsedFromJava("~", "$tilde")

  def test_eqeq_used_from_java(): Unit = testOperatorUsedFromJava("==", "$eq$eq")

  def test_less_used_from_java(): Unit = testOperatorUsedFromJava("<", "$less")

  def test_lesseq_used_from_java(): Unit = testOperatorUsedFromJava("<=", "$less$eq")

  def test_greater_used_from_java(): Unit = testOperatorUsedFromJava(">", "$greater")

  def test_greatereq_used_from_java(): Unit = testOperatorUsedFromJava(">=", "$greater$eq")

  def test_bang_used_from_java(): Unit = testOperatorUsedFromJava("!", "$bang")

  def test_percent_used_from_java(): Unit = testOperatorUsedFromJava("%", "$percent")

  def test_up_used_from_java(): Unit = testOperatorUsedFromJava("^", "$up")

  def test_amp_used_from_java(): Unit = testOperatorUsedFromJava("&", "$amp")

  def test_bar_used_from_java(): Unit = testOperatorUsedFromJava("|", "$bar")

  def test_times_used_from_java(): Unit = testOperatorUsedFromJava("*", "$times")

  def test_div_used_from_java(): Unit = testOperatorUsedFromJava("/", "$div")

  def test_bslash_used_from_java(): Unit = testOperatorUsedFromJava("\\", "$bslash")

  def test_qmark_used_from_java(): Unit = testOperatorUsedFromJava("?", "$qmark")

  private def testOperatorUsedFromScala(operatorName: String): Unit = {
    addScalaFile(
      s"""
         |object UsedOperator {
         |  val num = new Num(1)
         |  num $operatorName 1
         |"""
        .stripMargin, "UsedOperator")
    checkTextHasNoErrors(
      s"""
         | class Num(n: Int) {
         |   def $operatorName(n: Int): Num = new Num(n + this.n)
         | }
         |""".stripMargin)
  }

  def test_plus_used_from_scala(): Unit = testOperatorUsedFromScala("+")

  def test_minus_used_from_scala(): Unit = testOperatorUsedFromScala("-")

  def test_tilde_used_from_scala(): Unit = testOperatorUsedFromScala("~")

  def test_eqeq_used_from_scala(): Unit = testOperatorUsedFromScala("==")

  def test_less_used_from_scala(): Unit = testOperatorUsedFromScala("<")

  def test_lesseq_used_from_scala(): Unit = testOperatorUsedFromScala("<=")

  def test_greater_used_from_scala(): Unit = testOperatorUsedFromScala(">")

  def test_greatereq_used_from_scala(): Unit = testOperatorUsedFromScala(">=")

  def test_bang_used_from_scala(): Unit = testOperatorUsedFromScala("!")

  def test_percent_used_from_scala(): Unit = testOperatorUsedFromScala("%")

  def test_up_used_from_scala(): Unit = testOperatorUsedFromScala("^")

  def test_amp_used_from_scala(): Unit = testOperatorUsedFromScala("&")

  def test_bar_used_from_scala(): Unit = testOperatorUsedFromScala("|")

  def test_times_used_from_scala(): Unit = testOperatorUsedFromScala("*")

  def test_div_used_from_scala(): Unit = testOperatorUsedFromScala("/")

  def test_bslash_used_from_scala(): Unit = testOperatorUsedFromScala("\\")

  def test_qmark_used_from_scala(): Unit = testOperatorUsedFromScala("?")

  def test_literally_identified_function_declaration_used_from_scala(): Unit = {
    addScalaFile("object Ctx { new Foo().`bar`() }")
    checkTextHasNoErrors("class Foo { def `bar`(): Unit = {} }")
  }

  def test_literally_identified_function_declaration_used_from_java(): Unit = {
    addJavaFile("class Bar { void foo() { new Foo().bar(); } }")
    checkTextHasNoErrors("class Foo { def `bar`(): Unit = {} }")
  }

  def test_literally_identified_class_declaration_used_from_scala(): Unit = {
    addScalaFile("object Ctx { new `Foo` }")
    checkTextHasNoErrors("class `Foo`")
  }

  def test_literally_identified_class_declaration_used_from_java(): Unit = {
    addJavaFile("class Bar { void foo() { new Foo(); } }")
    checkTextHasNoErrors("class `Foo`")
  }

  def test_literally_identified_val_declaration_used_from_scala(): Unit = {
    addScalaFile("object Ctx { new Foo().`bar` }")
    checkTextHasNoErrors("class Foo { val `bar` = 42 }")
  }

  def test_literally_identified_val_declaration_used_from_java(): Unit = {
    addJavaFile("class Bar { void foo() { new Foo().bar; } }")
    checkTextHasNoErrors("class Foo { val `bar` = 42 }")
  }

  def test_single_abstract_method(): Unit = {
    addJavaFile(
      s"""import scala.annotation.unused
         |@unused class SamConsumer { @unused val samContainer: SamContainer = (i: Int) => println(i) }
         |""".stripMargin
    )
    checkTextHasNoErrors(
      s"""import scala.annotation.unused
         |abstract class SamContainer { def iAmSam(foobar: Int): Unit }
         |""".stripMargin
    )
  }
}
