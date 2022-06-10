package org.jetbrains.plugins.scala.codeInspection.shadow.positive

import org.jetbrains.plugins.scala.codeInspection.shadow.PrivateShadowInspectionTestBase

class PrivateShadowInspectionTest extends PrivateShadowInspectionTestBase {
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
