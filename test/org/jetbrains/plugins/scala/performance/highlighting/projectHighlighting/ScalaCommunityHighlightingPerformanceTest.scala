package org.jetbrains.plugins.scala.performance.highlighting.projectHighlighting

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.performance.ScalaCommunityGithubRepo
import org.junit.Ignore
import org.junit.experimental.categories.Category

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 10/23/15.
  */
@Ignore
@Category(Array(classOf[SlowTests]))
class ScalaCommunityHighlightingPerformanceTest extends PerformanceSbtProjectHighlightingTestBase
  with ScalaCommunityGithubRepo {

  def testPerformanceScalaCommunityScalaPsiUtil(): Unit = doTest("ScalaPsiUtil.scala", 25.seconds)

  def testPerformanceScalaCommunityScalaAnnotator(): Unit = doTest("ScalaAnnotator.scala", 10.seconds)

  def testPerformanceScalaCommunityScalaEvaluatorBuilderUtil(): Unit = doTest("ScalaEvaluatorBuilderUtil.scala", 14.seconds)

  def testPerformanceScalaCommunityConformance(): Unit = doTest("Conformance.scala", 11.seconds)

  def testPerformanceScalaCommunityScalaSpacingProcessor(): Unit = doTest("ScalaSpacingProcessor.scala", 5.seconds)
}
