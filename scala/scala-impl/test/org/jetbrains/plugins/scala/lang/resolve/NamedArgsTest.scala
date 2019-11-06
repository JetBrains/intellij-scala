package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author mucianm 
  * @since 05.04.16.
  */
class NamedArgsTest extends ScalaLightCodeInsightFixtureTestAdapter with SimpleResolveTestBase {
  import SimpleResolveTestBase._

  def testSCL9144(): Unit = {
    doResolveTest(
      s"""
        |class AB(val a: Int, val b: Int) {
        |  def withAB(x: Int) = ???
        |  def withAB(${REFTGT}a: Int = a, b: Int = b) = ???
        |  def withA(a: Int) = withAB(${REFSRC}a = a)
        |  def withB(b: Int) = withAB(b = b)
        |}
      """.stripMargin)
  }

  def testSCL14008(): Unit = {
    doResolveTest(
      s"""
        |class Type
        |  object Type {
        |    implicit def bool2Type(bool: Boolean): Type = new Type
        |  }
        |  class ToTargetType
        |  object ToTargetType {
        |    implicit def toType(toTargetType: ToTargetType): TargetType = new TargetType
        |  }
        |  class TargetType
        |  object Test {
        |    def apply(a: Int = 1, b: String = "2", ${REFTGT}c: Boolean = false): ToTargetType = new ToTargetType
        |    def apply(two: Type*): TargetType = new TargetType
        |  }
        |  object bar {
        |    val wasBroken: TargetType = Test.apply(${REFSRC}c = true)
        |  }
      """.stripMargin)
  }


}
