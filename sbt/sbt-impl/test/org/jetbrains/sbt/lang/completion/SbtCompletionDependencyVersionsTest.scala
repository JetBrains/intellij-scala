package org.jetbrains.sbt
package lang.completion

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

//noinspection ApiStatus
@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
final class SbtCompletionDependencyVersionsTest
  extends SbtFileTestDataCompletionTestBase
    with MockSbt_1_0 {

  private val GROUP_ID = "org.scalatest"
  private val versions = Seq("3.0.8", "3.0.8-RC1", "3.0.8-RC2", "3.0.8-RC3", "3.0.8-RC4", "3.0.8-RC5")

  def testCompleteVersion(): Unit = {
    DependencyUtil.updateMockVersionCompletionCache((GROUP_ID -> "scalatest") -> versions)
    doTest()
  }
}
