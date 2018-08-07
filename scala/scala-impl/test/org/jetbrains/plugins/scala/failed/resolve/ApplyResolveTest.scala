package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 15/04/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class ApplyResolveTest extends FailedResolveCaretTestBase {

  def testSCL13705(): Unit = {
    doResolveCaretTest(
      """
        |case class Test(c: String)
        |
        |trait Factory {
        |  def apply(c: String): String = c
        |}
        |
        |object Test extends Factory {
        |  Test("").<caret>toLowerCase
        |}
      """.stripMargin)
  }

  def testSCL14008(): Unit = {
    doResolveCaretTest(
      """
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
        |    def apply(a: Int = 1, b: String = "2", c: Boolean = false): ToTargetType = new ToTargetType
        |    def apply(two: Type*): TargetType = new TargetType
        |  }
        |  object bar {
        |    val wasBroken: TargetType = Test.apply(<caret>c = true)
        |  }
      """.stripMargin)
  }

}
