package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 04.04.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class ClashingCaseClassesTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override def shouldPass: Boolean = false

  def testSCL6146() = {
    myFixture.configureByText("Foo.scala",
      """
        |case class Foo()
      """.stripMargin)
    checkTextHasNoErrors(
      """
        |object SCL6146 {
        |  case class Foo()
        |}
        |class SCL6146 {
        |  import SCL6146._
        |  def foo(c: Any) = c match {
        |    case f: Foo =>
        |  }
        |
        |  def foo2 = Foo()
        |}
      """.stripMargin)
  }
}
