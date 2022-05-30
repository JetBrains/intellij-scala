package org.jetbrains.plugins.scala.codeInspection.shadowing.negative

import org.jetbrains.plugins.scala.codeInspection.shadowing.PrivateShadowInspectionTestBase

class PrivateShadowInspectionTest extends PrivateShadowInspectionTestBase {
  def test_subclass_field_doesnt_shadow_immutable_superclass_field(): Unit =
    checkTextHasNoErrors(
      s"""
         |class C(c: Int)
         |
         |class D(${START}c: Int${END}) extends C(c)
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
}
