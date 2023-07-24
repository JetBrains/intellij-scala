package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.Scala3UnusedDeclarationInspectionTestBase

class Scala3UnusedLocalDeclarationOneContainerInspectionTest extends Scala3UnusedDeclarationInspectionTestBase {

  def test_extension_method(): Unit =
    checkTextHasError(
      s"""
         |import scala.annotation.unused
         |@unused object Foo:
         |  extension(i: Int) { private def ${START}plus0$END: Int = i + 0 }
         |end Foo
         |""".stripMargin)

  // TODO -- Should enum cases be able to be annotated as unused?
  // Ideally "case Banana" in the below 2 tests is annotated as unused,
  // in order to be congruent with the philosophy used in most of the
  // negative and positive test packages.
  def test_enum(): Unit =
    checkTextHasError(
      s"""
         |import scala.annotation.unused
         |@unused object Foo:
         |  private enum ${START}Fruit$END { case ${START}Banana$END }
         |end Foo
         |""".stripMargin)

  def test_parameterized_enum(): Unit =
    checkTextHasError(
      s"""
         |import scala.annotation.unused
         |@unused object Foo:
         |  private enum ${START}Fruit$END(val i: Int = 42) { case ${START}Banana$END }
         |end Foo
         |""".stripMargin)

  def test_enum_case(): Unit =
    checkTextHasError(
      s"""
         |import scala.annotation.unused
         |@unused object Foo:
         |  private enum Fruit { case ${START}Banana$END, Strawberry }
         |  import Fruit.*
         |  Strawberry match { case Strawberry => }
         |end Foo
         |""".stripMargin)

  def test_parameterized_enum_case(): Unit =
    checkTextHasError(
      s"""
         |import scala.annotation.unused
         |@unused object Foo:
         |  private enum Fruit { case ${START}Banana$END(i: Int); case Strawberry(i: Int) }
         |  import Fruit.*
         |  Strawberry(42)
         |end Foo
         |""".stripMargin)
}

