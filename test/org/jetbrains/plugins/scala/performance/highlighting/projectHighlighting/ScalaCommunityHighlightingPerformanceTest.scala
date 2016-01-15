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

  override def revision: String = "752b3c8575ff506a249159c86bbfba3cdb96a317"

  def testPerformanceScalaCommunityScalaPsiUtil() = doTest("ScalaPsiUtil.scala", 25.seconds)

  def testPerformanceScalaCommunityScalaAnnotator() = doTest("ScalaAnnotator.scala", 10.seconds)

  def testPerformanceScalaCommunityScalaEvaluatorBuilderUtil() =
    doTest("ScalaEvaluatorBuilderUtil.scala", 14.seconds)

  def testPerformanceScalaCommunityConformance() = doTest("Conformance.scala", 11.seconds)

  def testPerformanceScalaCommunityScalaSpacingProcessor() = doTest("ScalaSpacingProcessor.scala", 5.seconds)

  override def doTest(path: String, timeout: Int): Unit = {
    VfsRootAccess.SHOULD_PERFORM_ACCESS_CHECK = false
    super.doTest(path, timeout)
  }
}
