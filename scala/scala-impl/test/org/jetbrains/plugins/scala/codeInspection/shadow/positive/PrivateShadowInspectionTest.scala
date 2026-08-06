package org.jetbrains.plugins.scala.codeInspection.shadow.positive

import org.jetbrains.plugins.scala.codeInspection.shadow.PrivateShadowInspectionTestBase

class PrivateShadowInspectionTest extends PrivateShadowInspectionTestBase {
  def test_subclass_parameter_shadows_superclass_mutable_field(): Unit =
    checkTextHasError(
      s"""
         |class C(var c: Int)
         |
         |class D(${START}c${END}: Int) extends C(c) {
         |  def usage = c
         |}
         |""".stripMargin
    )
}
