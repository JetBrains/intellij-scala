package org.jetbrains.plugins.scala.failed.types.existentialSimplification

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.types.existentialSimplification.ExistentialSimplificationTestBase
import org.junit.experimental.categories.Category

@Category(Array(classOf[PerfCycleTests]))
class ExistentialSimplificationAllRulesTest extends ExistentialSimplificationTestBase {
  override def folderPath: String = super.folderPath + "allRules/"

  def testAllRules() = doTest()
}