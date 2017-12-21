package org.jetbrains.plugins.scala.projectHighlighting

import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class CacheControlProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "playframework"

  override def githubRepoName = "cachecontrol"

  override def revision = "d46b3ff25e1e881ff037bec52664969807edf2a6"
}
