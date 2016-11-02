package org.jetbrains.plugins.scala.failed.decompiler

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

import scala.tools.scalap.DecompilerTestBase

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class FailedDecompilerTest extends DecompilerTestBase {
  override def basePath(separator: Char): String = s"${super.basePath(separator)}failed$separator"

  def testScl5865() = {
    doTest("$colon$colon.class")
  }

  def testScl7997() = {
    doTest("CommentDecompilation.class")
  }

  def testScl8251() = {
    doTest("LinkedEntry.class")
  }

  def testScl10858() = {
    doTest("LazyValBug.class")
  }
}
