package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.FlakyTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[FlakyTests]))
class FinchProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "finagle"

  override def githubRepoName = "finch"

  override def revision = "af94e61104f8e6cd15332227cf184cfe46a37666"

  override def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "generic/src/test/scala/io/finch/generic/GenericSpec.scala" -> Set((225, 255)),
    "core/src/test/scala/io/finch/syntax/MapperSyntaxSpec.scala" -> Set((2452, 2479)),
    "examples/src/main/scala/io/finch/div/Main.scala" -> Set((677, 807),(661, 674)),
    "examples/src/main/scala/io/finch/todo/Main.scala" -> Set((1339, 1659),(1318, 1332)),
  )
}
