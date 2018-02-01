package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.junit.experimental.categories.Category

/**
  * @author mucianm 
  * @since 04.04.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class StructuralsConformanceTest extends TypeConformanceTestBase {

  override protected def shouldPass: Boolean = false

  def testSCL5118(): Unit = {
    doTest(
      """
        |val a: {def toString : String} = "foo"
        |//True
      """.stripMargin)
  }

  def testSCL12540(): Unit = {
    doTest(
      """
        |trait Helper[A] {
        |  type Value = A
        |}
        |sealed trait Base
        |object Base extends Helper[Base] {
        |  case object Choice1 extends Value
        |  case object Choice2 extends Value
        |}
        |
        |val a: Base = Base.Choice1
        |//True
      """.stripMargin)
  }

  def testSCL12611(): Unit = {
    doTest(
      """
        |type Id = Short
        |final val InvalidId: Id = -1
        |//True
      """.stripMargin)
  }
}
