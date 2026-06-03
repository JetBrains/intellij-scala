package org.jetbrains.plugins.scala.codeInspection.shadow.negative

import org.jetbrains.plugins.scala.codeInspection.shadow.PrivateShadowInspectionTestBase

class PrivateShadowInspectionTest extends PrivateShadowInspectionTestBase {
  def test_subclass_parameter_doesnt_shadow_immutable_superclass_field(): Unit =
    checkTextHasNoErrors(
      s"""
         |class C(c: Int)
         |
         |class D(${START}c: Int${END}) extends C(c)
         |""".stripMargin
    )

  def test_highlight_doesnt_appear_if_parameter_is_not_used(): Unit =
    checkTextHasNoErrors(
      s"""
         |class C(var c: Int)
         |
         |class D(${START}c: Int${END}) extends C(0)
         |""".stripMargin
    )

  def test_local_field_doesnt_shadow_private_superclass_field(): Unit =
    checkTextHasNoErrors(
      s"""
         |  class A(a: Int)
         |
         |  class B extends A(0) {
         |    def bbb(): Int = {
         |      val a = 1
         |      a
         |    }
         |  }""".stripMargin
    )

  def test_subclass_field_doesnt_shadow_superclass_mutable_field(): Unit =
    checkTextHasNoErrors(
      s"""
         |class C(var c: Int)
         |
         |class D extends C(0) {
         |  private val ${START}c${END}: Int = 0
         |  def f: Int = c
         |}
         |""".stripMargin
    )


  def test_subclass_private_this_parameter_doesnt_shadow_superclass_mutable_field(): Unit =
    checkTextHasNoErrors(
      s"""
         |class C(var c: Int)
         |
         |class D(private[this] val ${START}c${END}: Int) extends C(c) {
         |  def usage = c
         |}
         |""".stripMargin
    )

  def test_subclass_parameter_not_highlighted_if_not_used_in_the_body(): Unit =
    checkTextHasNoErrors(
      s"""
         |class C(var c: Int)
         |
         |class D(${START}c${END}: Int) extends C(c)
         |""".stripMargin
    )

  def test_abstract_class_parameter_doesnt_shadow_trait_var(): Unit =
    checkTextHasNoErrors(
      s"""
         |trait Foo {
         |  var v: Int
         |}
         |
         |abstract class Bar(v: Int) extends Foo {
         |  println(v)
         |}
         |""".stripMargin
    )
}
