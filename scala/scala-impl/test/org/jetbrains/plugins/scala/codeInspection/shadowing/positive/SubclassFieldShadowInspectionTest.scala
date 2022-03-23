package org.jetbrains.plugins.scala.codeInspection.shadowing.positive

import org.jetbrains.plugins.scala.codeInspection.shadowing.FieldShadowInspectionTestBase

class SubclassFieldShadowInspectionTest extends FieldShadowInspectionTestBase {
  def test_subclass_field_shadows_superclass_mutable_field(): Unit =
    checkTextHasError(
      s"""
         |class C(var c: Int)
         |
         |class D(${START}c: Int${END}) extends C(c)
         |""".stripMargin
    )
}
