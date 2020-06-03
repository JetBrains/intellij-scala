package org.jetbrains.plugins.scala.projectHighlighting

import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class KorolevProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "fomkin"

  override def githubRepoName = "korolev"

  //v.0.9.0
  override def revision = "7d4d41113b574061aedb5a791747b0fa5d122fdf"
}
