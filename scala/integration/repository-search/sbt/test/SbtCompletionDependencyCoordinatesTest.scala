package org.jetbrains.plugins.scala.reposearch.sbt

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode
import org.jetbrains.sbt.MockSbt_1_0
import org.jetbrains.sbt.lang.completion.SbtFileTestDataCompletionTestBase

//noinspection ApiStatus
@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
final class SbtCompletionDependencyCoordinatesTest
  extends SbtFileTestDataCompletionTestBase
    with MockSbt_1_0 {

  private val GROUP_ID = "org.scalatest"

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "Requires type resolution")
  def testCompleteGroupArtifact(): Unit = {
    DependencyUtil.updateMockGroupIdCompletionCache(GROUP_ID)
    DependencyUtil.updateMockArtifactIdCompletionCache(GROUP_ID -> Seq("scalatest"))
    doTest()
  }
}
