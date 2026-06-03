package org.jetbrains.plugins.scala.codeInspection.internal

import org.junit.Assert.assertFalse

class ApiStatusInspectionPositiveTest extends ApiStatusInspectionTestBase {

  def test_import_in_same_module(): Unit = {
    val annotated = "@org.jetbrains.annotations.ApiStatus.Internal class Foo"
    val usage = "import Foo"
    myFixture.addFileToProject("module1/Foo.scala", annotated)
    val fileToHighlight = myFixture.addFileToProject("module1/Bar.scala", usage)
    assertFalse(hasError(fileToHighlight))
  }

  def test_import_in_different_module(): Unit = {
    val annotated = "@org.jetbrains.annotations.ApiStatus.Internal class Foo"
    val usage = "import Foo"
    myFixture.addFileToProject("module1/Foo.scala", annotated)
    val fileToHighlight = myFixture.addFileToProject("module2/Bar.scala", usage)
    assertFalse(hasError(fileToHighlight))
  }

  def test_class_in_same_module(): Unit = {
    val annotated = "@org.jetbrains.annotations.ApiStatus.Internal class Foo"
    val usage = "class Bar extends Foo"
    myFixture.addFileToProject("module1/Foo.scala", annotated)
    val fileToHighlight = myFixture.addFileToProject("module1/Bar.scala", usage)
    assertFalse(hasError(fileToHighlight))
  }

  def test_method_in_same_module(): Unit = {
    val annotated = "object Foo { @org.jetbrains.annotations.ApiStatus.Internal def bar(): Unit = () }"
    val usage = "object Bar { Foo.bar() }"
    myFixture.addFileToProject("module1/Foo.scala", annotated)
    val fileToHighlight = myFixture.addFileToProject("module1/Bar.scala", usage)
    assertFalse(hasError(fileToHighlight))
  }

  def test_val_in_same_module(): Unit = {
    val annotated = "object Foo { @org.jetbrains.annotations.ApiStatus.Internal val bar = 3 }"
    val usage = "object Bar { Foo.bar }"
    myFixture.addFileToProject("module1/Foo.scala", annotated)
    val fileToHighlight = myFixture.addFileToProject("module1/Bar.scala", usage)
    assertFalse(hasError(fileToHighlight))
  }
}
