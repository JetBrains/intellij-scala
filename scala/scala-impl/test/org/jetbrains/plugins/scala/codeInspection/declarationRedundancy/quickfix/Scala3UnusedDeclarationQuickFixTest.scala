package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.quickfix

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.Scala3UnusedDeclarationInspectionTestBase

class Scala3UnusedDeclarationQuickFixTest extends Scala3UnusedDeclarationInspectionTestBase {

  def test_extension_method(): Unit = {
    val text =
      s"""
         |extension(i: Int)
         |  def ext1: Int = i + 0
         |  def ext2: Int = i + 1
         |0.ext1
         |""".stripMargin
    val expected =
      s"""
         |extension(i: Int)
         |  def ext1: Int = i + 0
         |0.ext1
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_enum(): Unit = testQuickFix("enum Foo { case Bar }", "", removeUnusedElementHint)

  def test_parameterized_enum(): Unit = testQuickFix("enum Foo(val i: Int) { case Bar }", "", removeUnusedElementHint)

  def test_enum_case(): Unit = {
    val text =
      s"""
         |@scala.annotation.unused object Foo:
         |  enum Fruit:
         |    case Strawberry, Banana
         |  end Fruit
         |  Fruit.Strawberry match { case _ => }
         |end Foo
         |""".stripMargin
    val expected =
      s"""
         |@scala.annotation.unused object Foo:
         |  enum Fruit:
         |    case Strawberry
         |  end Fruit
         |  Fruit.Strawberry match { case _ => }
         |end Foo
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_enum_case3(): Unit = {
    val text =
      s"""
         |@scala.annotation.unused object Foo:
         |  enum Fruit:
         |    case Strawberry
         |    case Banana
         |  end Fruit
         |  Fruit.Strawberry match { case _ => }
         |end Foo
         |""".stripMargin
    val expected =
      s"""
         |@scala.annotation.unused object Foo:
         |  enum Fruit:
         |    case Strawberry
         |  end Fruit
         |  Fruit.Strawberry match { case _ => }
         |end Foo
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  /*
  The below tests are shelved as part of SCL-20352.

  Summary of what happened: An attempt was made to enable unused declaration inspection
  for implicit parameters. This seemed to work fine, so it was approved and merged.

  Later Timur discovered that the QuickFixes that were available after these changes for
  some cases involving 'using' did not work well. So the below tests were written as part
  of a fix that addressed this.

  Then we discovered the problems detailed in SCL-20352. We decided to revert the changes
  made for implicit parameters. This invalidated the very tests that are commented out below.
  */

  // TODO -- Once we have a sustainable implementation of unused declaration inspection for implicit
  //  parameters, uncomment the below tests and they should Just Work.

  /* ===== START OF SHELVED TESTS AS PART OF SCL-20352 (don't touch)
  def test_single_named_using_param(): Unit = {
    val text =
      s"""
         |def foo(using s: String) = ()
         |
         |foo(using "boo")
         |""".stripMargin
    val expected =
      s"""
         |def foo() = ()
         |
         |foo()
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_single_anonymous_using_param(): Unit = {
    val text =
      s"""
         |def foo(using String) = ()
         |
         |foo(using "boo")
         |""".stripMargin
    val expected =
      s"""
         |def foo() = ()
         |
         |foo()
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_named_using_param_first(): Unit = {
    val text =
      s"""
         |import scala.annotation.unused
         |def foo(using s: String, @unused i: Int, @unused d: Double) = ()
         |
         |foo(using "boo", 42, 0.0)
         |""".stripMargin
    val expected =
      s"""
         |import scala.annotation.unused
         |def foo(using @unused i: Int, @unused d: Double) = ()
         |
         |foo(using 42, 0.0)
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_named_using_param_in_the_middle(): Unit = {
    val text =
      s"""
         |import scala.annotation.unused
         |def foo(using @unused i: Int, s: String, @unused d: Double) = ()
         |
         |foo(using 42, "boo", 0.0)
         |""".stripMargin
    val expected =
      s"""
         |import scala.annotation.unused
         |def foo(using @unused i: Int, @unused d: Double) = ()
         |
         |foo(using 42, 0.0)
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_named_using_param_last(): Unit = {
    val text =
      s"""
         |import scala.annotation.unused
         |def foo(using @unused i: Int, @unused d: Double, s: String) = ()
         |
         |foo(using 42, 0.0, "boo")
         |""".stripMargin
    val expected =
      s"""
         |import scala.annotation.unused
         |def foo(using @unused i: Int, @unused d: Double) = ()
         |
         |foo(using 42, 0.0)
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_anonymous_using_param_first(): Unit = {
    val text =
      s"""
         |import scala.annotation.unused
         |def foo(using String, @unused Int, @unused Double) = ()
         |
         |foo(using "boo", 42, 0.0)
         |""".stripMargin
    val expected =
      s"""
         |import scala.annotation.unused
         |def foo(using @unused Int, @unused Double) = ()
         |
         |foo(using 42, 0.0)
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_anonymous_using_param_in_the_middle(): Unit = {
    val text =
      s"""
         |import scala.annotation.unused
         |def foo(using @unused Int, String, @unused Double) = ()
         |
         |foo(using 42, "boo", 0.0)
         |""".stripMargin
    val expected =
      s"""
         |import scala.annotation.unused
         |def foo(using @unused Int, @unused Double) = ()
         |
         |foo(using 42, 0.0)
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_anonymous_using_param_last(): Unit = {
    val text =
      s"""
         |import scala.annotation.unused
         |def foo(using @unused Int, @unused Double, String) = ()
         |
         |foo(using 42, 0.0, "boo")
         |""".stripMargin
    val expected =
      s"""
         |import scala.annotation.unused
         |def foo(using @unused Int, @unused Double) = ()
         |
         |foo(using 42, 0.0)
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_named_using_param_first_clause(): Unit = {
    val text =
      s"""
         |import scala.annotation.unused
         |@unused def foo(using s: String)(using @unused Int)(using @unused Boolean) = ()
         |""".stripMargin
    val expected =
      s"""
         |import scala.annotation.unused
         |@unused def foo(using @unused Int)(using @unused Boolean) = ()
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_named_using_param_clause_in_the_middle(): Unit = {
    val text =
      s"""
         |import scala.annotation.unused
         |@unused def foo(using @unused Int)(using s: String)(using @unused Boolean) = ()
         |""".stripMargin
    val expected =
      s"""
         |import scala.annotation.unused
         |@unused def foo(using @unused Int)(using @unused Boolean) = ()
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_named_using_param_last_clause(): Unit = {
    val text =
      s"""
         |import scala.annotation.unused
         |@unused def foo(using @unused Int)(using @unused Boolean)(using s: String) = ()
         |""".stripMargin
    val expected =
      s"""
         |import scala.annotation.unused
         |@unused def foo(using @unused Int)(using @unused Boolean) = ()
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_anonymous_using_param_first_clause(): Unit = {
    val text =
      s"""
         |import scala.annotation.unused
         |@unused def foo(using String)(using @unused Int)(using @unused Boolean) = ()
         |""".stripMargin
    val expected =
      s"""
         |import scala.annotation.unused
         |@unused def foo(using @unused Int)(using @unused Boolean) = ()
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_anonymous_using_param_clause_in_the_middle(): Unit = {
    val text =
      s"""
         |import scala.annotation.unused
         |@unused def foo(using @unused Int)(using String)(using @unused Boolean) = ()
         |""".stripMargin
    val expected =
      s"""
         |import scala.annotation.unused
         |@unused def foo(using @unused Int)(using @unused Boolean) = ()
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }

  def test_anonymous_using_param_last_clause(): Unit = {
    val text =
      s"""
         |import scala.annotation.unused
         |@unused def foo(using @unused Int)(using @unused Boolean)(using String) = ()
         |""".stripMargin
    val expected =
      s"""
         |import scala.annotation.unused
         |@unused def foo(using @unused Int)(using @unused Boolean) = ()
         |""".stripMargin
    testQuickFix(text, expected, removeUnusedElementHint)
  }
  ===== END OF SHELVED TESTS AS PART OF SCL-20352 */

  def test_last_enum_case(): Unit = {
    val text = "@scala.annotation.unused enum Fruit { case Banana }"
    checkNotFixable(text, removeUnusedElementHint)
  }

  def test_last_extension_method(): Unit = {
    val text = "extension(i: Int) { def ext1: Int = i + 0 }"
    checkNotFixable(text, removeUnusedElementHint)
  }
}
