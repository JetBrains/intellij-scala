package org.jetbrains.plugins.scala.codeInspection.unused.quickfix

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedSymbolInspectionTestBase
import org.junit.Assert.assertTrue

class Scala3UnusedSymbolQuickFixTest extends ScalaUnusedSymbolInspectionTestBase{

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

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
    testQuickFix(text, expected, hint)
  }

  def test_enum(): Unit = testQuickFix("enum Foo { case Bar }", "", hint)

  def test_parameterized_enum(): Unit = testQuickFix("enum Foo(val i: Int) { case Bar }", "", hint)

  def test_enum_case1(): Unit = {
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
    testQuickFix(text, expected, hint)
  }

  def test_enum_case2(): Unit = {
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
    testQuickFix(text, expected, hint)
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
    testQuickFix(text, expected, hint)
  }

  def test_last_enum_case(): Unit = {
    val text = "@scala.annotation.unused enum Fruit { case Banana }"
    checkNotFixable(text, hint)
  }

  def test_last_extension_method(): Unit = {
    val text = "extension(i: Int) { def ext1: Int = i + 0 }"
    checkNotFixable(text, hint)
  }
}
