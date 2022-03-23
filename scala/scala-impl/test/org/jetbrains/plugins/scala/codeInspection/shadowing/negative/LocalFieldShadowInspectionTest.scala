package org.jetbrains.plugins.scala.codeInspection.shadowing.negative

import org.jetbrains.plugins.scala.codeInspection.shadowing.FieldShadowInspectionTestBase

class LocalFieldShadowInspectionTest extends FieldShadowInspectionTestBase {
  def test_local_field_shadows_nothing(): Unit =
    checkTextHasNoErrors(
      s"""
         |  class Foo {
         |    def bar(): Unit = {
         |      val ${START}foo${END}: String = "foo"
         |    }
         |  }
         |""".stripMargin
    )

  def test_local_field_doesnt_shadow_private_superclass_field(): Unit =
    checkTextHasNoErrors(
      s"""
         |  class Foo(private val foo: String)
         |
         |  class Bar extends Foo("bar") {
         |    def bar(): Unit = {
         |      val ${START}foo${END}: String = "foo"
         |      foo
         |    }
         |  }
         |""".stripMargin
    )
}