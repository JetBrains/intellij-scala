package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.resolve2.ResolveTestBase
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 02.04.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class StructuralTypeTest extends FailedResolveTest("structuralType") {
  def testSCL6894() = doTest()
}
