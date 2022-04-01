package org.jetbrains.plugins.scala.codeInspection.unused.quickfix

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedDeclarationInspectionTestBase

class Scala3UnusedDeclarationQuickFixTest extends ScalaUnusedDeclarationInspectionTestBase{

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

  def test_last_enum_case(): Unit = {
    val text = "@scala.annotation.unused enum Fruit { case Banana }"
    checkNotFixable(text, removeUnusedElementHint)
  }

  def test_last_extension_method(): Unit = {
    val text = "extension(i: Int) { def ext1: Int = i + 0 }"
    checkNotFixable(text, removeUnusedElementHint)
  }
}
