package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class Scala3UsedLocalDeclarationOneContainerInspectionTest extends ScalaUnusedDeclarationInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  def test_extension_method(): Unit =
    checkTextHasNoErrors(
      s"""
         |import scala.annotation.unused
         |@unused object Foo:
         |  extension(i: Int) { private def plus0: Int = i + 0 }
         |  0.plus0
         |end Foo
         |""".stripMargin)

  def test_enum(): Unit =
    checkTextHasNoErrors(
      s"""
         |import scala.annotation.unused
         |@unused object Foo:
         |  private enum Fruit { case Banana, Strawberry }
         |  import Fruit.*
         |  Strawberry match { case Banana => }
         |end Foo
         |""".stripMargin)

  def test_parameterized_enum(): Unit =
    checkTextHasNoErrors(
      s"""
         |import scala.annotation.unused
         |@unused object Foo:
         |  private enum Fruit(val i: Int = 42) { case Banana }
         |  import Fruit.*
         |  Banana match { case Banana => }
         |end Foo
         |""".stripMargin)

  def test_parameterized_enum_case(): Unit =
    checkTextHasNoErrors(
      s"""
         |import scala.annotation.unused
         |@unused object Foo:
         |  private enum Fruit { case Banana(i: Int); case Strawberry(i: Int) }
         |  import Fruit.*
         |  Strawberry(42) match { case _: Banana => }
         |end Foo
         |""".stripMargin)
}
