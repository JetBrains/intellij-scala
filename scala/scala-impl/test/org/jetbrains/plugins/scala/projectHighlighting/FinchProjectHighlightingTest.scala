package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class FinchProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "finagle"

  override def githubRepoName = "finch"

  override def revision = "af94e61104f8e6cd15332227cf184cfe46a37666"

  override def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "EndpointSpec.scala" -> Set((2346, 2354), (2337, 2345)),
    "EndpointMapper.scala" -> Set((311, 314), (304, 310)),
    "Endpoint.scala" -> Set((7183,7185), (7186,7189)),
    "GenericSpec.scala" -> Set((225, 255))
  )
}
