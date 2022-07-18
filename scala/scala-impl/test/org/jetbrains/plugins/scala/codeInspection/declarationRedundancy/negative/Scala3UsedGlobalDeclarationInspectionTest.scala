package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class Scala3UsedGlobalDeclarationInspectionTest extends ScalaUnusedDeclarationInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  private def addFile(text: String): Unit = myFixture.addFileToProject("Foo.scala", text)

  def test_extension_method(): Unit = {
    addFile("object Bar { import Foo.*; 0.plus0 }")
    checkTextHasNoErrors("object Foo { extension(i: Int) { def plus0: Int = i + 0 } }")
  }

  def test_enum(): Unit = {
    addFile("object Bar { import Foo.Language.*; Spanish match { case Spanish => } }")
    checkTextHasNoErrors("object Foo { enum Language { case Spanish } }")
  }

  def test_parameterized_enum(): Unit = {
    addFile("object Bar { import Foo.Fruit.*; Strawberry match { case s: Strawberry => s.i } }")
    checkTextHasNoErrors("object Foo { enum Fruit(val i: Int = 42) { case Strawberry } }")
  }

  def test_parameterized_enum_case(): Unit = {
    addFile("object Bar { import Foo.Fruit.*; Strawberry(42) match { case s: Strawberry => s.i } }")
    checkTextHasNoErrors("object Foo { enum Fruit { case Strawberry(i: Int) } }")
  }

  def test_enum_case_usage_by_construction(): Unit = {
    addFile("object Bar { import Foo.Fruit.*; Strawberry(42); Mango }")
    checkTextHasNoErrors("object Foo { enum Fruit { case Mango; case Strawberry(i: Int) } }")
  }

}
