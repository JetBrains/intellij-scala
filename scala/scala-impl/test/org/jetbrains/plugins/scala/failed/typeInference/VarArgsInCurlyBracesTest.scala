package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SimpleTestCase}
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 28.03.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class VarArgsInCurlyBracesTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  def testSCL3856() = {
    val code =
      """
        |val array = Array[String]()
        |Set.apply[String]{ array: _* }
      """.stripMargin
    checkTextHasNoErrors(code)
  }
}
