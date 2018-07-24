package org.jetbrains.plugins.scala.projectHighlighting

import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class Log4sProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "Log4s"

  override def githubRepoName = "log4s"

  override def revision = "8439aef843da2c9f489d1dff4cf62df6135fb9d8"

  override def filesWithProblems = Map.empty
}
