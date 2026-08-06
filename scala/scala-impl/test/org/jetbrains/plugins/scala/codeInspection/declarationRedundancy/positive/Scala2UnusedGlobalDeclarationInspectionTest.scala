package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class Scala2UnusedGlobalDeclarationInspectionTest extends ScalaUnusedDeclarationInspectionTestBase {
  private def addFile(text: String): Unit = myFixture.addFileToProject("Foo.scala", text)

  private def doOperatorTest(operatorName: String): Unit = checkTextHasError(
    s"""@scala.annotation.unused class Num(n: Int) {
       |  def $START$operatorName$END(n: Int): Num = new Num(n + this.n)
       |}
       |""".stripMargin
  )


  def test_plus(): Unit = doOperatorTest("+")

  def test_minus(): Unit = doOperatorTest("-")

  def test_tilde(): Unit = doOperatorTest("~")

  def test_eqeq(): Unit = doOperatorTest("==")

  def test_less(): Unit = doOperatorTest("<")

  def test_lesseq(): Unit = doOperatorTest("<=")

  def test_greater(): Unit = doOperatorTest(">")

  def test_greatereq(): Unit = doOperatorTest(">=")

  def test_bang(): Unit = doOperatorTest("!")

  def test_percent(): Unit = doOperatorTest("%")

  def test_up(): Unit = doOperatorTest("^")

  def test_amp(): Unit = doOperatorTest("&")

  def test_bar(): Unit = doOperatorTest("|")

  def test_times(): Unit = doOperatorTest("*")

  def test_div(): Unit = doOperatorTest("/")

  def test_bslash(): Unit = doOperatorTest("\\")

  def test_qmark(): Unit = doOperatorTest("?")

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

  def test_val_with_same_name_as_method(): Unit = {
    addFile("object A { def bar() = ??? }")
    checkTextHasError(s"@scala.annotation.unused object B { val ${START}bar$END = 42 }")
  }

  def test_type_with_same_name_as_another_declaration(): Unit = {
    addFile("object A { val Foo = 42 }")
    checkTextHasError(s"@scala.annotation.unused object B { type ${START}Foo$END }")
  }

  def test_class_that_is_only_used_by_itself_via_class_parameter_type(): Unit = checkTextHasError(
    s"class ${START}A$END(@scala.annotation.unused a: A)"
  )

  def test_class_that_is_only_used_by_itself_via_method_return_type(): Unit = checkTextHasError(
    s"class ${START}A$END { @scala.annotation.unused def foo: A = null }"
  )

  def test_class_that_is_only_used_by_itself_via_method_parameter_type(): Unit = checkTextHasError(
    s"""import scala.annotation.unused
       |class ${START}A$END { @unused def foo(@unused a :A) = () }""".stripMargin
  )

  def test_class_that_is_only_used_by_itself_via_new_template_definition(): Unit = checkTextHasError(
    s"class ${START}A$END { @scala.annotation.unused def foo = new A }"
  )

  def test_object_is_only_used_by_itself(): Unit = checkTextHasError(
    s"object ${START}A$END { private val x = 42; println(A.x) }"
  )

  def test_trait_that_is_only_used_by_itself_via_method_return_type(): Unit = checkTextHasError(
    s"trait ${START}A$END { @scala.annotation.unused def foo: A }"
  )

  def test_trait_that_is_only_used_by_itself_via_method_parameter_type(): Unit = checkTextHasError(
    s"trait ${START}A$END { @scala.annotation.unused def foo(a :A): Unit }"
  )
}
