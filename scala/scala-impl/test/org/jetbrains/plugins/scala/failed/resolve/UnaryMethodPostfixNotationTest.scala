package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.resolve2.ResolveTestBase
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 29.03.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class UnaryMethodPostfixNotationTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "bug3/"
  }

  def testSCL7704() = doTest()
}
