package org.jetbrains.plugins.scala.codeInspection.shadowing.positive

import org.jetbrains.plugins.scala.codeInspection.shadowing.FieldShadowInspectionTestBase

class LocalFieldShadowInspectionTest extends FieldShadowInspectionTestBase {
  def test_local_field_shadows_class_field(): Unit =
    checkTextHasError(
    s"""
       |  class Foo {
       |    val foo: String = "foo"
       |
       |    def bar(): Unit = {
       |      val ${START}foo${END}: String = "foo"
       |      foo
       |    }
       |  }
       |""".stripMargin
  )

  def test_local_field_shadows_class_method(): Unit =
    checkTextHasError(
      s"""
         |  class Foo {
         |    def foo(str: String): String = str
         |
         |    def bar(): Unit = {
         |      val ${START}foo${END}: String = "foo"
         |      foo
         |    }
         |  }
         |""".stripMargin
    )

  def test_local_field_shadows_private_class_field(): Unit =
    checkTextHasError(
      s"""
         |  class Foo {
         |    private val foo: String = "foo"
         |
         |    def bar(): Unit = {
         |      val ${START}foo${END}: String = "foo"
         |      foo
         |    }
         |  }
         |""".stripMargin
    )

  def test_local_field_shadows_class_parameter(): Unit =
    checkTextHasError(
      s"""
         |  class Foo(foo: String) {
         |    def bar(): Unit = {
         |      val ${START}foo${END}: String = "foo"
         |      foo
         |    }
         |  }
         |""".stripMargin
    )

  def test_local_field_shadows_class_implicit_parameter(): Unit =
    checkTextHasError(
      s"""
         |  class Foo(n: Int)(implicit foo: String) {
         |    def bar(): Unit = {
         |      val ${START}foo${END}: String = "foo"
         |      foo
         |    }
         |  }
         |""".stripMargin
    )

  def test_local_field_shadows_trait_field(): Unit =
    checkTextHasError(
      s"""
         |  trait Foo {
         |    val foo: String
         |
         |    def bar(): Unit = {
         |      val ${START}foo${END}: String = "foo"
         |      foo
         |    }
         |  }
         |""".stripMargin
    )
}
