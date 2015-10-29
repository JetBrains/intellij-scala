package org.jetbrains.plugins.scala.performance.highlighting.projectHighlighting

import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import org.jetbrains.plugins.scala.SlowTests
import org.junit.experimental.categories.Category

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 10/23/15.
  */
@Category(Array(classOf[SlowTests]))
class ScalaCommunityHighlightingPerformanceTest extends PerformanceSbtProjectHighlightingTestBase {

  override protected def getExternalSystemConfigFileName: String = "build.sbt"

  override def githubUsername: String = "JetBrains"

  override def githubRepoName: String = "intellij-scala"

  override def revision: String = "493444f6465d0eddea75ac5cd5a848cc30d48ae5"

  def testPerformanceScalaCommunityScalaPsiUtil() = doTest("ScalaPsiUtil.scala", 24 * 1000)

  def testPerformanceScalaCommunityScalaAnnotator() = doTest("ScalaAnnotator.scala", 10 * 1000)

  def testPerformanceScalaCommunityScalaEvaluatorBuilderUtil() =
    doTest("ScalaEvaluatorBuilderUtil.scala", 14 * 1000)

  def testPerformanceScalaCommunityConformance() = doTest("Conformance.scala", 11 * 1000)

  def testPerformanceScalaCommunityScalaSpacingProcessor() = doTest("ScalaSpacingProcessor.scala", 6 * 1000)

  override def doTest(path: String, timeout: Int): Unit = {
    VfsRootAccess.SHOULD_PERFORM_ACCESS_CHECK = false
    super.doTest(path, timeout)
  }
}
