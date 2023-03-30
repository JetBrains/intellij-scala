package org.jetbrains.plugins.scala.codeInspection.internal

import org.junit.Assert.assertTrue

class ApiStatusInspectionNegativeTest extends ApiStatusInspectionTestBase {
  def test_class_in_different_module(): Unit = {
    val annotated = "@org.jetbrains.annotations.ApiStatus.Internal class Foo"
    val usage = "class Bar extends Foo"
    myFixture.addFileToProject("module1/Foo.scala", annotated)
    val fileToHighlight = myFixture.addFileToProject("module2/Bar.scala", usage)
    assertTrue(hasError(fileToHighlight))
  }

  def test_method_in_different_module(): Unit = {
    val annotated = "object Foo { @org.jetbrains.annotations.ApiStatus.Internal def bar(): Unit = () }"
    val usage = "object Bar { Foo.bar() }"
    myFixture.addFileToProject("module1/Foo.scala", annotated)
    val fileToHighlight = myFixture.addFileToProject("module2/Bar.scala", usage)
    assertTrue(hasError(fileToHighlight))
  }

  def test_val_in_different_module(): Unit = {
    val annotated = "object Foo { @org.jetbrains.annotations.ApiStatus.Internal val bar = 3 }"
    val usage = "object Bar { Foo.bar }"
    myFixture.addFileToProject("module1/Foo.scala", annotated)
    val fileToHighlight = myFixture.addFileToProject("module2/Bar.scala", usage)
    assertTrue(hasError(fileToHighlight))
  }

  def test_java_class_non_default_constructor(): Unit = {
    val annotated = "public class Foo { Foo() {} @org.jetbrains.annotations.ApiStatus.Internal Foo(int i) {} }"
    val usage = "class Bar extends Foo(1)"
    myFixture.addFileToProject("module1/Foo.java", annotated)
    val fileToHighlight = myFixture.addFileToProject("module2/Bar.scala", usage)
    assertTrue(hasError(fileToHighlight))
  }
}
