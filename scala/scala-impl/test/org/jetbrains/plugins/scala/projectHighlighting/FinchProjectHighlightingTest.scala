package org.jetbrains.plugins.scala.projectHighlighting

import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class FinchProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "finagle"

  override def githubRepoName = "finch"

  override def revision = "af94e61104f8e6cd15332227cf184cfe46a37666"

  override def filesWithProblems: Map[String, Seq[(Int, Int)]] = Map(
    "EndpointSpec.scala" -> Seq((2346, 2354), (2337, 2345), (2346, 2354), (2337, 2345)),
    "ToResponse.scala" -> Seq((1717, 1774), (1717, 1774), (1894, 1951), (1894, 1951)),
    "package.scala" -> Seq(),
    "EndpointMapper.scala" -> Seq((311, 314), (304, 310), (304, 310)),
    "ServerSentEvent.scala" -> Seq((563, 642), (563, 642), (508, 556))
  )
}
