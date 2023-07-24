package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.Scala3UnusedDeclarationInspectionTestBase

class Scala3UnusedGlobalDeclarationInspectionTest extends Scala3UnusedDeclarationInspectionTestBase {

  private def addFile(text: String): Unit = myFixture.addFileToProject("Foo.scala", text)

  def test_extension_method(): Unit = {
    addFile("object Bar { import Foo.* }")
    checkTextHasError(s"object Foo { extension(i: Int) { def ${START}plus0$END: Int = i + 0 } }")
  }

  def test_enum(): Unit = {
    addFile("object Bar { import Foo.* }")
    checkTextHasError(s"object Foo { enum ${START}Language$END { case ${START}Spanish$END } }")
  }

  def test_enum_case(): Unit = {
    addFile("object Bar { import Foo.Language.*; Spanish match { case Spanish => } }")
    checkTextHasError(s"object Foo { enum Language { case ${START}English$END, Spanish } }")
  }

  def test_parameterized_enum(): Unit = {
    addFile("object Bar { import Foo.* }")
    checkTextHasError(s"object Foo { enum ${START}Fruit$END(val i: Int = 42) { case ${START}Strawberry$END } }")
  }

  def test_parameterized_enum_case(): Unit = {
    addFile("object Bar { import Foo.Fruit.* }")
    checkTextHasError(s"object Foo { enum Fruit { case ${START}Strawberry$END(i: Int) } }")
  }
}
