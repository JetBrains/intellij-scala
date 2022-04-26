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

  def test_object_field_shadows_trait_field(): Unit =
    checkTextHasError(
      s"""
         |trait TraitA {
         |  def n: Int = 1
         |}
         |
         |object ObjectA extends TraitA {
         |  def foo(): Unit = {
         |    val ${START}n${END}: Int = 0
         |  }
         |}
         |""".stripMargin
    )
}
