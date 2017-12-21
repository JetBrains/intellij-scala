package org.jetbrains.plugins.scala.projectHighlighting

import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class MeerkatProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "meerkat-parser"

  override def githubRepoName = "Meerkat"

  override def revision = "3e59173f1efcefff6b9e8a120f1fee30bd3d7403"

  override def filesWithProblems: Map[String, Seq[(Int, Int)]] = Map(
    "Example12.scala" -> Seq(),
    "Example10.scala" -> Seq(),
    "package.scala" -> Seq((4162, 4227),(4162, 4227),(4125, 4155)),
    "Example4.scala" -> Seq(),
    "Example14.scala" -> Seq(),
    "Example13.scala" -> Seq((1771, 1774),(1771, 1774),(1850, 1851),(1850, 1851),(1856, 1859),(1856, 1859),(1862, 1863)),
    "Example8.scala" -> Seq(),
    "Example9.scala" -> Seq((1850, 2053),(1850, 2053),(1823, 1842)),
    "Parsers.scala" -> Seq(),
    "Example1.scala" -> Seq(),
    "SPPFVisitor.scala" -> Seq((9108, 9151)),
    "AbstractOperatorParsers.scala" -> Seq(),
    "OperatorParsers.scala" -> Seq(),
    "Example11.scala" -> Seq(),
    "Example3.scala" -> Seq(),
    "Example2.scala" -> Seq(),
    "Example5.scala" -> Seq((1654, 1915),(1654, 1915),(1640, 1651)),
    "Example15.scala" -> Seq(),
    "Example6.scala" -> Seq(),
    "AbstractParsers.scala" -> Seq(),
    "DDParsers.scala" -> Seq((8402, 8434),(9438, 9470)),
    "Example7.scala" -> Seq()
  )
}
