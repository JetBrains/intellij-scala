package org.jetbrains.plugins.scala.performance.highlighting.projectHighlighting

import org.jetbrains.plugins.scala.SlowTests
import org.junit.Ignore
import org.junit.experimental.categories.Category

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 10/25/15.
  */
@Ignore
@Category(Array(classOf[SlowTests]))
class ScalazHighlightingPerformanceTest extends PerformanceSbtProjectHighlightingTestBase {
  override protected def getExternalSystemConfigFileName: String = "project/build.scala"

  override def githubUsername: String = "scalaz"

  override def githubRepoName: String = "scalaz"

  override def revision: String = "de8391722269fea4e09229f3bbfa68c08e0b8cd8"

  def testApplyScalazPerformance(): Unit = doTest("Apply.scala", 5.seconds)

  def testImmutableArrayScalazPerformance(): Unit = doTest("ImmutableArray.scala", 6.seconds)

  def testFoldableScalazPerformance(): Unit = doTest("Foldable.scala", 6.seconds)

  def testIListScalazPerformance(): Unit = doTest("IList.scala", 6.seconds)
}
