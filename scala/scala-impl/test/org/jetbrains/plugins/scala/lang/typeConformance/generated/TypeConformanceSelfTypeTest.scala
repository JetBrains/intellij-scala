package org.jetbrains.plugins.scala.lang.typeConformance.generated

import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase

class TypeConformanceSelfTypeTest extends TypeConformanceTestBase {
  def testSCL7914(): Unit = {
    val text =
      s"""trait Test {
         |  self: Outer =>
         |
         |  val inner: Inner = new Inner
         |  ${caretMarker}val outerInner: Outer#Inner = inner
         |}
         |
         |class Outer {
         |  class Inner
         |}
         |//true
      """.stripMargin
    doTest(text)
  }

  def testThisTypeConformsSelfType(): Unit = {
    val text =
      s"""
        |trait T
        |
        |trait C {
        |  self: T =>
        |
        |  ${caretMarker}val y: T = (this: this.type)
        |}
        |//true
      """.stripMargin
    doTest(text)
  }

  def testClassTypeSelfTypeNoConformance(): Unit = {
    val text =
      s"""
         |trait T
         |
         |trait C {
         |  self: T =>
         |
         |  ${caretMarker}val y: T = (this: C)
         |}
         |//false
      """.stripMargin
    doTest(text)
  }

  // A class type does not conform to its own `this.type`: `C.this.type` denotes one instance.
  def testClassTypeDoesNotConformToThisType(): Unit = {
    val text =
      s"""
         |class C {
         |  ${caretMarker}val y: this.type = (this: C)
         |}
         |//false
      """.stripMargin
    doTest(text)
  }

  def testOtherInstanceDoesNotConformToThisType(): Unit = {
    val text =
      s"""
         |class C {
         |  def f(other: C): Unit = {
         |    ${caretMarker}val y: C.this.type = other
         |  }
         |}
         |//false
      """.stripMargin
    doTest(text)
  }

  def testThisConformsToThisType(): Unit = {
    val text =
      s"""
         |class C {
         |  ${caretMarker}val y: this.type = this
         |}
         |//true
      """.stripMargin
    doTest(text)
  }
}