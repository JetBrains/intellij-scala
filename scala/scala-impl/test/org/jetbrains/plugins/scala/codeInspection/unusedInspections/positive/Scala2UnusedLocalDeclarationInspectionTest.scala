package org.jetbrains.plugins.scala.codeInspection.unusedInspections.positive

import org.jetbrains.plugins.scala.codeInspection.unusedInspections.ScalaUnusedDeclarationInspectionTestBase

class Scala2UnusedLocalDeclarationInspectionTest extends ScalaUnusedDeclarationInspectionTestBase {

  def test_private_field(): Unit =
    checkTextHasError(s"@scala.annotation.unused class Foo { private val ${START}s$END = 0}")

  def test_val(): Unit = {
    val code =
      s"""
         |@scala.annotation.unused object Foo {
         |  @scala.annotation.unused def foo(): Unit = {
         |    val ${START}v$END = 0
         |  }
         |}
      """.stripMargin
    checkTextHasError(code)
  }

  def test_var(): Unit = {
    val code =
      s"""
         |@scala.annotation.unused class Bar {
         |  @scala.annotation.unused def aa(): Unit = {
         |    var ${START}v$END = 0
         |  }
         |}
      """.stripMargin
    checkTextHasError(code)
  }

  def test_multiple_declarations_single_type(): Unit = {
    val code =
      s"""
         |@scala.annotation.unused class Foo {
         |  private val ${START}a$END, b: String = ""
         |  println(b)
         |}
      """.stripMargin
    checkTextHasError(code)
  }

  def test_multiple_declarations_multiple_types(): Unit = {
    val code =
      s"""
         |@scala.annotation.unused class Foo {
         |  private val (${START}a$END, b) = ("", 42)
         |  println(b)
         |}
      """.stripMargin
    checkTextHasError(code)
  }

  def test_match_case_with_type(): Unit = {
    val code =
      s"""
         |class Moo {
         |  Option(null) match {
         |    case Some(${START}s$END: String) =>
         |      println("AA")
         |  }
         |}
         |new Moo
      """.stripMargin
    checkTextHasError(code)
  }

  def test_match_case_without_type(): Unit = {
    val code =
      s"""
         |class Moo {
         |  Option(null) match {
         |    case Some(${START}s$END) =>
         |      println("AA")
         |  }
         |}
         |new Moo
      """.stripMargin
    checkTextHasError(code)
  }

  def test_anonymous_function_destructor(): Unit = {
    val code =
      s"""
         |class Moo {
         |  Option("").map {
         |    case ${START}a$END: String =>
         |  }
         |}
         |new Moo
      """.stripMargin
    checkTextHasError(code)
  }

  def test_binding_pattern(): Unit = {
    val code =
      s"""
         |class Moo {
         |  Option(null) match {
         |    case ${START}s$END@Some(a) => println(a)
         |  }
         |}
         |new Moo
      """.stripMargin
    checkTextHasError(code)
  }

  def test_binding_pattern_2(): Unit = {
    val code =
      s"""
         |class Moo {
         |  Option(null) match {
         |    case s@Some(${START}a$END) => println(s)
         |  }
         |}
         |new Moo
      """.stripMargin
    checkTextHasError(code)
  }

  def test_anonymous_function_with_case_clause(): Unit = {
    val code =
      s"""
         |class Moo {
         |  def foo(s: Seq[(Int, Int)]): Seq[Int] = {
         |    s.map {
         |      case (a, ${START}b$END) => a
         |    }
         |  }
         |}
         |new Moo().foo(Seq.empty)
      """.stripMargin
    checkTextHasError(code)
  }

  def test_unused_regular_anonymous_function(): Unit = {
    val code =
      s"""
         |class Moo {
         |  def foo(s: Seq[(Int, Int)]): Seq[Int] = {
         |    s.map (${START}a$END => 1)
         |  }
         |}
         |new Moo().foo(Seq.empty)
      """.stripMargin
    checkTextHasError(code)
  }

  def test_for(): Unit = {
    val code =
      s"""
         |class Moo {
         |  val s = Seq("")
         |  for (${START}j$END <- s) {
         |    println(s)
         |  }
         |}
         |new Moo
      """.stripMargin
    checkTextHasError(code)
  }

  def test_class(): Unit = {
    val code =
      s"""
         |class Person() {
         |  def func() = {
         |    object ${START}A$END
         |  }
         |}
         |new Person().func()
      """.stripMargin
    checkTextHasError(code)
  }

  def test_inner_class(): Unit = {
    val code =
      s"""
         |class Person() {
         |  private object ${START}A$END
         |}
         |new Person
      """.stripMargin
    checkTextHasError(code)
  }

  def test_public_type_member_in_public_trait(): Unit = checkTextHasError(
    s"""
       |trait Test {
       |  type ${START}ty$END = Int
       |}
       |object Test extends Test
       |Test
       |""".stripMargin
  )

  def test_private_type_member_in_public_trait(): Unit = checkTextHasError(
    s"""
       |trait Test {
       |  private type ${START}ty$END = Int
       |}
       |object Test extends Test
       |Test
       |""".stripMargin
  )

  def test_public_type_member_in_private_trait(): Unit = checkTextHasError(
    s"""
       |private trait Test {
       |  type ${START}ty$END = Int
       |}
       |object Test extends Test
       |Test
       |""".stripMargin
  )

  def test_property_assignment(): Unit = checkTextHasError(
    s"""object Test {
       |  private def data: Int = 0
       |  private def ${START}data_=$END(i: Int): Int = i
       |
       |  println(Test.data)
       |}
       |""".stripMargin
  )

  def test_class_type_parameter1(): Unit = checkTextHasError(
    s"""
       |@scala.annotation.unused class Test[${START}A$END]
       |""".stripMargin
  )

  def test_class_type_parameter2(): Unit = checkTextHasError(
    s"""
       |@scala.annotation.unused class Test[A, ${START}B$END] { Seq.empty[A] }
       |""".stripMargin
  )

  def test_function_type_parameter1(): Unit = checkTextHasError(
    s"""
       |@scala.annotation.unused class Test {
       |  @scala.annotation.unused def foo[${START}A$END] = {}
       |}
       |""".stripMargin
  )

  def test_function_type_parameter2(): Unit = checkTextHasError(
    s"""
       |@scala.annotation.unused class Test {
       |  @scala.annotation.unused def foo[A, ${START}B$END] = { Seq.empty[A] }
       |}
       |""".stripMargin
  )

  def test_implicit_private_this_implicit_parameter(): Unit = checkTextHasError(
    s"""
       |@scala.annotation.unused
       |class MyClass()
       |  (implicit ${START}param$END: Boolean) {
       |}
       |""".stripMargin
  )

  def test_private_this_implicit_parameter(): Unit = checkTextHasError(
    s"""
       |@scala.annotation.unused
       |class MyClass()
       |  (implicit private[this] val ${START}param$END: Boolean) {
       |}
       |""".stripMargin
  )

  def test_private_implicit_parameter(): Unit = checkTextHasError(
    s"""
       |@scala.annotation.unused
       |class MyClass()
       |  (implicit private val ${START}param$END: Boolean) {
       |}
       |""".stripMargin
  )

  def test_private_implicit_val(): Unit = checkTextHasError(
    s"""
       |@scala.annotation.unused
       |class MyClass() {
       |  implicit private val ${START}param$END: Boolean = false
       |}
       |""".stripMargin
  )

  def test_single_abstract_method(): Unit = checkTextHasError(
    s"private abstract class ${START}SamContainer$END { def ${START}iAmSam$END(i: Int): Unit }"
  )
}