package org.jetbrains.plugins.scala.codeInspection.unused.negative

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedSymbolInspectionTestBase

class Scala3UsedLocalSymbolTwoContainersInspectionTest extends ScalaUnusedSymbolInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  def test_extension_method(): Unit =
    checkTextHasNoErrors(
      s"""
         |import scala.annotation.unused
         |object Foo:
         |  extension(i: Int)
         |    def plus0: Int = i + 0
         |end Foo
         |@unused object Bar { import Foo.*; 0.plus0 }
         |""".stripMargin)

  def test_enum(): Unit =
    checkTextHasNoErrors(
      s"""
         |import scala.annotation.unused
         |object Foo { enum Fruit { case Banana } }
         |@unused object Bar:
         |  import Foo.Fruit.*
         |  Banana match { case Banana => }
         |end Bar
         |""".stripMargin)

  def test_parameterized_enum(): Unit =
    checkTextHasNoErrors(
      s"""
         |import scala.annotation.unused
         |object Foo { enum Fruit(@unused i: Int) { case Banana extends Fruit(42) } }
         |@unused object Bar:
         |  import Foo.Fruit.*
         |  Banana match { case Banana => }
         |end Bar
         |""".stripMargin)

  def test_parameterized_enum_case(): Unit =
    checkTextHasNoErrors(
      s"""
         |import scala.annotation.unused
         |object Foo { enum Fruit { case Banana(@unused i: Int) } }
         |@unused object Bar:
         |  import Foo.Fruit.*
         |  Banana(42) match { case _: Banana => }
         |end Bar
         |""".stripMargin)
}

