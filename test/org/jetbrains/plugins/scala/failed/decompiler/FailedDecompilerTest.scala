package org.jetbrains.plugins.scala.failed.decompiler

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.decompiler.DecompilerTestBase
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class FailedDecompilerTest extends DecompilerTestBase {
  override def basePath(separator: Char): String = s"${super.basePath(separator)}failed$separator"

  def testScl7997() = {
    doTest("CommentDecompilation.class")
  }
}
