package org.jetbrains.plugins.scala.projectHighlighting.downloaded

import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.projectHighlighting.base.GithubRepositoryWithRevision

class MeerkatProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("niktrop", "Meerkat", "5013864a9cbcdb43f92d1d57200352743d412235")

  override def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_8

  import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

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
    "src/main/scala/org/meerkat/parsers/AbstractOperatorParsers.scala" -> Set(),
    "src/main/scala/org/meerkat/parsers/OperatorParsers.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example11.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example3.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example2.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example5.scala" -> Set((1654, 1915),(1640, 1651)),
    "src/test/scala/org/meerkat/parsers/examples/Example15.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example6.scala" -> Set(),
    "src/main/scala/org/meerkat/parsers/AbstractParsers.scala" -> Set(),
    "src/test/scala/org/meerkat/parsers/examples/Example7.scala" -> Set()
  )
}
