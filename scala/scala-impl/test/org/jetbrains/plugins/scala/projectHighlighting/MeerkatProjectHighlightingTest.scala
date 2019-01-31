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
    "src/test/scala/org/meerkat/parsers/examples/Example12.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example10.scala" -> Set(),
    "src/main/scala/org/meerkat/parsers/package.scala" -> Set((4162, 4227),(4125, 4155)),
    "src/test/scala/org/meerkat/parsers/examples/Example4.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example14.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example13.scala" -> Set((1771, 1774),(1850, 1851),(1856, 1859),(1862, 1863)),
    "src/test/scala/org/meerkat/parsers/examples/Example8.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example9.scala" -> Set(),
    "src/main/scala/org/meerkat/parsers/Parsers.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example1.scala" -> Set(),
    "src/main/scala/org/meerkat/sppf/SPPFVisitor.scala" -> Set((9108, 9151)),
    "src/main/scala/org/meerkat/parsers/AbstractOperatorParsers.scala" -> Set(),
    "src/main/scala/org/meerkat/parsers/OperatorParsers.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example11.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example3.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example2.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example5.scala" -> Set((1654, 1915),(1640, 1651)),
    "src/test/scala/org/meerkat/parsers/examples/Example15.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example6.scala" -> Set(),
    "src/main/scala/org/meerkat/parsers/AbstractParsers.scala" -> Set(),
    "src/main/scala/org/meerkat/parsers/DDParsers.scala" -> Set((8336,8347),(8402, 8434),(9372,9383),(9438, 9470)),
    "src/test/scala/org/meerkat/parsers/examples/Example7.scala" -> Set()
  )
}
