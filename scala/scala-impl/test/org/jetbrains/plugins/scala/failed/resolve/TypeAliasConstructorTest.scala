package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 31.03.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class TypeAliasConstructorTest extends FailedResolveTest("typeAlias") {
  def testSCL13742(): Unit = doTest()
}
