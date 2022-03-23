package org.jetbrains.plugins.scala.codeInspection.shadowing.negative

import org.jetbrains.plugins.scala.codeInspection.shadowing.FieldShadowInspectionTestBase

class SubclassFieldShadowInspectionTest extends FieldShadowInspectionTestBase {
  def test_subclass_field_shadows_immutable_superclass_field(): Unit =
    checkTextHasNoErrors(
      s"""
         |class C(c: Int)
         |
         |class D(${START}c: Int${END}) extends C(c)
         |""".stripMargin
    )
}
