package org.jetbrains.plugins.scala.codeInspection.shadowing.positive

import org.jetbrains.plugins.scala.codeInspection.shadowing.PrivateShadowInspectionTestBase

class PrivateShadowInspectionTest extends PrivateShadowInspectionTestBase {
  def test_subclass_parameter_shadows_superclass_mutable_field(): Unit =
    checkTextHasError(
      s"""
         |class C(var c: Int)
         |
         |class D(${START}c: Int${END}) extends C(c)
         |""".stripMargin
    )

  def test_subclass_field_shadows_superclass_mutable_field(): Unit =
    checkTextHasError(
      s"""
         |class C(var c: Int)
         |
         |class D extends C(0) {
         |  private val ${START}c${END}: Int = 0
         |  def f: Int = c
         |}
         |""".stripMargin
    )
}
