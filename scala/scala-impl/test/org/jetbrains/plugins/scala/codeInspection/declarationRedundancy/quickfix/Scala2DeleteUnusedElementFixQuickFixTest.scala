package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.quickfix

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class Scala2DeleteUnusedElementFixQuickFixTest extends ScalaUnusedDeclarationInspectionTestBase {

  def test_private_field(): Unit = {
    val before =
      """
        |@scala.annotation.unused class Foo {
        |  private val s = 0
        |}
      """.stripMargin
    val after =
      """
        |@scala.annotation.unused class Foo {
        |}
      """.stripMargin
    testQuickFix(before, after, removeUnusedElementHint)
  }

  def test_local_unused_declaration(): Unit = {
    val before =
      """
        |object Foo {
        |  def foo(): Unit = {
        |    val s = 0
        |  }
        |}
        |Foo.foo()
      """.stripMargin
    val after =
      """
        |object Foo {
        |  def foo(): Unit = {
        |  }
        |}
        |Foo.foo()
      """.stripMargin
    testQuickFix(before, after, removeUnusedElementHint)
  }

  def test_multiple_declarations_single_type(): Unit = {
    val before =
      """
        |@scala.annotation.unused class Foo {
        |  private val a, b: String = ""
        |  println(b)
        |}
      """.stripMargin
    val after =
      """
        |@scala.annotation.unused class Foo {
        |  private val b: String = ""
        |  println(b)
        |}
      """.stripMargin
    testQuickFix(before, after, removeUnusedElementHint)
  }

  def test_multiple_declarations_multiple_types(): Unit = {
    val before =
      """
        |@scala.annotation.unused class Foo {
        |  private val (a, b) = ("", 42)
        |  println(b)
        |}
      """.stripMargin
    val after =
      """
        |@scala.annotation.unused class Foo {
        |  private val (_, b) = ("", 42)
        |  println(b)
        |}
      """.stripMargin
    testQuickFix(before, after, removeUnusedElementHint)
  }

  def test_var(): Unit = {
    val before =
      s"""
         |class Bar {
         |  def aa(): Unit = {
         |    var (d, a) = 10
         |    println(a)
         |  }
         |}
         |new Bar().aa()
      """.stripMargin
    val after =
      s"""
         |class Bar {
         |  def aa(): Unit = {
         |    var (_, a) = 10
         |    println(a)
         |  }
         |}
         |new Bar().aa()
      """.stripMargin
    testQuickFix(before, after, removeUnusedElementHint)
  }

  def test_match_case_with_type(): Unit = {
    val before =
      """
        |class Moo {
        |  Option(null) match {
        |    case Some(s: String) =>
        |      println("AA")
        |  }
        |}
        |new Moo
      """.stripMargin
    val after =
      """
        |class Moo {
        |  Option(null) match {
        |    case Some(_: String) =>
        |      println("AA")
        |  }
        |}
        |new Moo
      """.stripMargin
    testQuickFix(before, after, removeUnusedElementHint)
  }

  def test_match_case_without_type(): Unit = {
    val before =
      """
        |class Moo {
        |  Option(null) match {
        |    case Some(s) =>
        |      println("AA")
        |  }
        |}
        |new Moo
      """.stripMargin
    val after =
      """
        |class Moo {
        |  Option(null) match {
        |    case Some(_) =>
        |      println("AA")
        |  }
        |}
        |new Moo
      """.stripMargin
    testQuickFix(before, after, removeUnusedElementHint)
  }

  def test_anonymous_function_destructor(): Unit = {
    val before =
      """
        |class Moo {
        |  Option("").map {
        |    case a: String =>
        |  }
        |}
        |new Moo
      """.stripMargin
    val after =
      """
        |class Moo {
        |  Option("").map {
        |    case _: String =>
        |  }
        |}
        |new Moo
      """.stripMargin
    testQuickFix(before, after, removeUnusedElementHint)
  }

  def test_binding_pattern(): Unit = {
    val before =
      """
        |class Moo {
        |  Option(null) match {
        |    case s@Some(a) => println(a)
        |  }
        |}
        |new Moo
      """.stripMargin
    val after =
      """
        |class Moo {
        |  Option(null) match {
        |    case Some(a) => println(a)
        |  }
        |}
        |new Moo
      """.stripMargin
    testQuickFix(before, after, removeUnusedElementHint)
  }

  def test_binding_pattern_2(): Unit = {
    val before =
      """
        |class Moo {
        |  Option(null) match {
        |    case s@Some(a) => println(s)
        |  }
        |}
        |new Moo
      """.stripMargin
    val after =
      """
        |class Moo {
        |  Option(null) match {
        |    case s@Some(_) => println(s)
        |  }
        |}
        |new Moo
      """.stripMargin
    testQuickFix(before, after, removeUnusedElementHint)
  }

  def test_anonymous_function_with_case_clause(): Unit = {
    val before =
      """
        |class Moo {
        |  def foo(s: Seq[(Int, Int)]): Seq[Int] = {
        |    s.map {
        |      case (a, b) => a
        |    }
        |  }
        |}
        |new Moo().foo(Seq.empty)
      """.stripMargin
    val after =
      """
        |class Moo {
        |  def foo(s: Seq[(Int, Int)]): Seq[Int] = {
        |    s.map {
        |      case (a, _) => a
        |    }
        |  }
        |}
        |new Moo().foo(Seq.empty)
      """.stripMargin
    testQuickFix(before, after, removeUnusedElementHint)
  }

  def test_unused_regular_anonymous_function(): Unit = {
    val before =
      """
        |class Moo {
        |  def foo(s: Seq[(Int, Int)]): Seq[Int] = {
        |    s.map (a => 1)
        |  }
        |}
        |new Moo().foo(Seq.empty)
      """.stripMargin
    val after =
      """
        |class Moo {
        |  def foo(s: Seq[(Int, Int)]): Seq[Int] = {
        |    s.map (_ => 1)
        |  }
        |}
        |new Moo().foo(Seq.empty)
      """.stripMargin
    testQuickFix(before, after, removeUnusedElementHint)
  }

  def test_for(): Unit = {
    val before =
      """
        |class Moo {
        |  val s = Seq("")
        |  for (j <- s) {
        |    println(s)
        |  }
        |}
        |new Moo
      """.stripMargin
    val after =
      """
        |class Moo {
        |  val s = Seq("")
        |  for (_ <- s) {
        |    println(s)
        |  }
        |}
        |new Moo
      """.stripMargin
    testQuickFix(before, after, removeUnusedElementHint)
  }

  def test_class(): Unit = {
    val before =
      """
        |class Person() {
        |  def func() = {
        |    object A
        |  }
        |}
        |new Person().func()
      """.stripMargin
    val after =
      """
        |class Person() {
        |  def func() = {
        |
        |  }
        |}
        |new Person().func()
      """.stripMargin
    testQuickFix(before, after, removeUnusedElementHint)
  }

  def test_inner_class(): Unit = {
    val before =
      """
        |class Person() {
        |  private object A
        |}
        |new Person
      """.stripMargin
    val after =
      """
        |class Person() {
        |
        |}
        |new Person
      """.stripMargin
    testQuickFix(before, after, removeUnusedElementHint)
  }

  def test_side_effect_definition(): Unit = {
    val text =
      """
        |object Test {
        |  def test(): Int = 3
        |  private val x = test()
        |}
        |""".stripMargin

    testQuickFix(
      text,
      """
        |object Test {
        |  def test(): Int = 3
        |}
        |""".stripMargin,
      hintWholeDefinition
    )

    testQuickFix(
      text,
      """
        |object Test {
        |  def test(): Int = 3
        |
        |  test()
        |}
        |""".stripMargin,
      hintOnlyXBinding
    )
  }

  def test_class_type_parameter1(): Unit = {
    val text = "@scala.annotation.unused class Test[A]"
    val expected = "@scala.annotation.unused class Test"
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_class_type_parameter2(): Unit = {
    val text = "@scala.annotation.unused class Test[A, B] { Seq.empty[A] }"
    val expected = "@scala.annotation.unused class Test[A] { Seq.empty[A] }"
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_function_type_parameter1(): Unit = {
    val text =
      s"""
         |@scala.annotation.unused class Test {
         |  @scala.annotation.unused def foo[A] = {}
         |}
         |""".stripMargin
    val expected =
      s"""
         |@scala.annotation.unused class Test {
         |  @scala.annotation.unused def foo = {}
         |}
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_function_type_parameter2(): Unit = {
    val text =
      s"""
         |@scala.annotation.unused class Test {
         |  @scala.annotation.unused def foo[A, B] = { Seq.empty[A] }
         |}
         |""".stripMargin
    val expected =
      s"""
         |@scala.annotation.unused class Test {
         |  @scala.annotation.unused def foo[A] = { Seq.empty[A] }
         |}
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }
}
