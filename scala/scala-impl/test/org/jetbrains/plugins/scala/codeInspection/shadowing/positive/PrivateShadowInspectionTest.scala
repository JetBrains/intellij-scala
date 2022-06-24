package org.jetbrains.plugins.scala.codeInspection.shadowing.positive

import org.jetbrains.plugins.scala.codeInspection.shadowing.PrivateShadowInspectionTestBase

class PrivateShadowInspectionTest extends PrivateShadowInspectionTestBase {
  def test_subclass_parameter_shadows_superclass_mutable_field(): Unit =
    checkTextHasError(
      s"""
         |class C(var c: Int)
         |
         |class D(${START}c${END}: Int) extends C(c)
         |""".stripMargin
    )

  def test_subclass_parameter_shadows_abstract_class_var(): Unit =
    checkTextHasError(
      s"""
         |abstract class Animal(var cat: String)
         |
         |class Cat(${START}cat${END}: String = "cat") extends Animal("") {
         |  cat
         |}
         |""".stripMargin
    )
}
