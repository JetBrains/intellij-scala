package org.jetbrains.plugins.scala.failed.checkAccess

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.checkers.checkPrivateAccess.CheckPrivateAccessTestBase
import org.junit.experimental.categories.Category

/**
  * User: Dmitry.Naydanov
  * Date: 22.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class CheckAccessTest extends CheckPrivateAccessTestBase {
  override def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "failed/"

  def testSCL9212() = doTest()
}
