package org.jetbrains.plugins.scala.projectHighlighting

import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category


/**
  * @author mutcianm
  * @since 16.05.17.
  */

@Category(Array(classOf[HighlightingTests]))
class ArnoldCHighlightingTestAll extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "lhartikk"
  override def githubRepoName = "ArnoldC"
  override def revision = "3dd905be59525f0b9a04e0baa6fd6acab09db8ea"
}
