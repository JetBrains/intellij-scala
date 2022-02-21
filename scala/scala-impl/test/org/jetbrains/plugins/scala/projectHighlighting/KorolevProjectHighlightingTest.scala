package org.jetbrains.plugins.scala.projectHighlighting

import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class KorolevProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "fomkin"

  override def githubRepoName = "korolev"

  //v.1.1.0
  override def revision = "362bdce4989084b8d8c98f15a627326113a6da70"
}
