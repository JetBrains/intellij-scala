package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase

/**
  * @author mucianm 
  * @since 07.04.16.
  */
class BufferedSourceTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override protected def shouldPass: Boolean = false

  def testSCL3582(): Unit = {
    doResolveTest(s"scala.io.${REFSRC}BufferedSource")
  }
}
