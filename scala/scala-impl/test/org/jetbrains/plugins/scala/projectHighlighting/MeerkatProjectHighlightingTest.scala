package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class MeerkatProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "niktrop"

  override def githubRepoName = "Meerkat"

  override def revision = "5013864a9cbcdb43f92d1d57200352743d412235"

  override def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "Example12.scala" -> Set(),
    "Example10.scala" -> Set(),
    "package.scala" -> Set((4162, 4227),(4125, 4155)),
    "Example4.scala" -> Set(),
    "Example14.scala" -> Set(),
    "Example13.scala" -> Set((1771, 1774),(1850, 1851),(1856, 1859),(1862, 1863)),
    "Example8.scala" -> Set(),
    "Example9.scala" -> Set(),
    "Parsers.scala" -> Set(),
    "Example1.scala" -> Set(),
    "SPPFVisitor.scala" -> Set((9108, 9151)),
    "AbstractOperatorParsers.scala" -> Set(),
    "OperatorParsers.scala" -> Set(),
    "Example11.scala" -> Set(),
    "Example3.scala" -> Set(),
    "Example2.scala" -> Set(),
    "Example5.scala" -> Set((1654, 1915),(1640, 1651)),
    "Example15.scala" -> Set(),
    "Example6.scala" -> Set(),
    "AbstractParsers.scala" -> Set(),
    "DDParsers.scala" -> Set((8336,8347),(8402, 8434),(9372,9383),(9438, 9470)),
    "Example7.scala" -> Set()
  )
}
