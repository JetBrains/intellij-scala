package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase
import org.junit.experimental.categories.Category

/**
  * @author mucianm 
  * @since 07.04.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class BufferedSourceTest extends ScalaLightCodeInsightFixtureTestAdapter with SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override protected def shouldPass: Boolean = false

  def testSCL3582(): Unit = {
    doResolveTest(s"scala.io.${REFSRC}BufferedSource")
  }
}
