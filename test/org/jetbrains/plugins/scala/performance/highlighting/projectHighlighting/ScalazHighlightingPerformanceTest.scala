package org.jetbrains.plugins.scala.performance.highlighting.projectHighlighting

import java.util.concurrent.TimeUnit

import org.jetbrains.plugins.scala.SlowTests
import org.junit.experimental.categories.Category

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 10/25/15.
  */
@Category(Array(classOf[SlowTests]))
class ScalazHighlightingPerformanceTest extends PerformanceSbtProjectHighlightingTestBase {
  override protected def getExternalSystemConfigFileName: String = "project/build.scala"

  override def githubUsername: String = "scalaz"

  override def githubRepoName: String = "scalaz"

  override def revision: String = "de8391722269fea4e09229f3bbfa68c08e0b8cd8"

  def testApplyScalazPerformance(): Unit = doTest("Apply.scala", TimeUnit.SECONDS.toMillis(3))

  def testImmutableArrayScalazPerformance(): Unit = doTest("ImmutableArray.scala", TimeUnit.SECONDS.toMillis(5))

  def testFoldableScalazPerformance(): Unit = doTest("Foldable.scala", TimeUnit.SECONDS.toMillis(14))

  def testIListScalazPerformance(): Unit = doTest("IList.scala", TimeUnit.SECONDS.toMillis(14))
}
