package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase

/**
  * @author mucianm 
  * @since 07.04.16.
  */
class BufferedSourceTest extends ScalaLightCodeInsightFixtureTestAdapter with SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override protected def shouldPass: Boolean = false

  def testSCL3582(): Unit = {
    doResolveTest(s"scala.io.${REFSRC}BufferedSource")
  }
}
