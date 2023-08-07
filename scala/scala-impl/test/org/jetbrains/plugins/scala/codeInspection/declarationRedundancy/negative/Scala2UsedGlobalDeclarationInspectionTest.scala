package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import com.intellij.ide.highlighter.XmlFileType
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class Scala2UsedGlobalDeclarationInspectionTest extends ScalaUnusedDeclarationInspectionTestBase {

  private def addScalaFile(text: String): Unit = myFixture.addFileToProject(s"Foo.scala", text)

  private def addJavaFile(text: String): Unit = myFixture.addFileToProject(s"Foo.java", text)

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
      """object UnusedConstructor {
        |  val foo1 = new Foo()
        |  val foo2 = new Foo("foo")
        |}""".stripMargin)

    checkTextHasNoErrors(
      """import scala.annotation.unused
        |@unused class Foo(@unused foo: String, @unused n: Int) {
        |  def this() = this("foo", 0)
        |  def this(str: String) = this(str, 0)
        |}""".stripMargin)
  }

  def test_overloaded_methods(): Unit = {
    addScalaFile(
      """object UnusedConstructor {
        |  val foo = new Foo()
        |  foo.aaa()
        |  foo.aaa("foo")
        |}""".stripMargin)

    checkTextHasNoErrors(
      """import scala.annotation.unused
        |@unused class Foo{
        |  def aaa(): Unit = {}
        |  def aaa(@unused str: String): Unit = {}
        |}""".stripMargin)
  }

  private def doTestOperatorUsedFromJava(operatorName: String, javaMethodName: String): Unit = {
    addJavaFile(
      s"""public class Foo {
         |  public static void main(String[] args) {
         |    new Num(1).$javaMethodName(1);
         |  }
         |}""".stripMargin)

    checkTextHasNoErrors(
      s"""class Num(n: Int) {
         |  def $operatorName(n: Int): Num = new Num(n + this.n)
         |}""".stripMargin)
  }

  def test_plus_used_from_java(): Unit = doTestOperatorUsedFromJava("+", "$plus")

  def test_minus_used_from_java(): Unit = doTestOperatorUsedFromJava("-", "$minus")

  def test_tilde_used_from_java(): Unit = doTestOperatorUsedFromJava("~", "$tilde")

  def test_eqeq_used_from_java(): Unit = doTestOperatorUsedFromJava("==", "$eq$eq")

  def test_less_used_from_java(): Unit = doTestOperatorUsedFromJava("<", "$less")

  def test_lesseq_used_from_java(): Unit = doTestOperatorUsedFromJava("<=", "$less$eq")

  def test_greater_used_from_java(): Unit = doTestOperatorUsedFromJava(">", "$greater")

  def test_greatereq_used_from_java(): Unit = doTestOperatorUsedFromJava(">=", "$greater$eq")

  def test_bang_used_from_java(): Unit = doTestOperatorUsedFromJava("!", "$bang")

  def test_percent_used_from_java(): Unit = doTestOperatorUsedFromJava("%", "$percent")

  def test_up_used_from_java(): Unit = doTestOperatorUsedFromJava("^", "$up")

  def test_amp_used_from_java(): Unit = doTestOperatorUsedFromJava("&", "$amp")

  def test_bar_used_from_java(): Unit = doTestOperatorUsedFromJava("|", "$bar")

  def test_times_used_from_java(): Unit = doTestOperatorUsedFromJava("*", "$times")

  def test_div_used_from_java(): Unit = doTestOperatorUsedFromJava("/", "$div")

  def test_bslash_used_from_java(): Unit = doTestOperatorUsedFromJava("\\", "$bslash")

  def test_qmark_used_from_java(): Unit = doTestOperatorUsedFromJava("?", "$qmark")

  private def doTestOperatorUsedFromScala(operatorName: String): Unit = {
    addScalaFile(
      s"""object Foo {
         |  val num = new Num(1)
         |  num $operatorName 1
         |}""".stripMargin)

    checkTextHasNoErrors(
      s"""class Num(n: Int) {
         |   def $operatorName(n: Int): Num = new Num(n + this.n)
         |}""".stripMargin)
  }

  def test_plus_used_from_scala(): Unit = doTestOperatorUsedFromScala("+")

  def test_minus_used_from_scala(): Unit = doTestOperatorUsedFromScala("-")

  def test_tilde_used_from_scala(): Unit = doTestOperatorUsedFromScala("~")

  def test_eqeq_used_from_scala(): Unit = doTestOperatorUsedFromScala("==")

  def test_less_used_from_scala(): Unit = doTestOperatorUsedFromScala("<")

  def test_lesseq_used_from_scala(): Unit = doTestOperatorUsedFromScala("<=")

  def test_greater_used_from_scala(): Unit = doTestOperatorUsedFromScala(">")

  def test_greatereq_used_from_scala(): Unit = doTestOperatorUsedFromScala(">=")

  def test_bang_used_from_scala(): Unit = doTestOperatorUsedFromScala("!")

  def test_percent_used_from_scala(): Unit = doTestOperatorUsedFromScala("%")

  def test_up_used_from_scala(): Unit = doTestOperatorUsedFromScala("^")

  def test_amp_used_from_scala(): Unit = doTestOperatorUsedFromScala("&")

  def test_bar_used_from_scala(): Unit = doTestOperatorUsedFromScala("|")

  def test_times_used_from_scala(): Unit = doTestOperatorUsedFromScala("*")

  def test_div_used_from_scala(): Unit = doTestOperatorUsedFromScala("/")

  def test_bslash_used_from_scala(): Unit = doTestOperatorUsedFromScala("\\")

  def test_qmark_used_from_scala(): Unit = doTestOperatorUsedFromScala("?")

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
    addScalaFile(
      """import scala.annotation.unused
        |@unused class SamConsumer { @unused val samContainer: SamContainer = (i: Int) => println(i) }
        |""".stripMargin
    )
    checkTextHasNoErrors("abstract class SamContainer { def iAmSam(foobar: Int): Unit }")
  }

  def test_structural_type_members(): Unit = checkTextHasNoErrors(
    """import scala.annotation.unused
      |@unused object A { @unused val v: { def foo: Int } = ??? }
      |""".stripMargin
  )

  def test_type_alias(): Unit = {
    addScalaFile("object B { val f: A.Foo = 42 }")
    checkTextHasNoErrors("@scala.annotation.unused object A { type Foo = Int }")
  }

  def test_extension_point_implementation(): Unit = {
    configureFromFileText(XmlFileType.INSTANCE, "org.foo.Bar")
    checkTextHasNoErrors("package org.foo\nclass Bar")
  }
}
