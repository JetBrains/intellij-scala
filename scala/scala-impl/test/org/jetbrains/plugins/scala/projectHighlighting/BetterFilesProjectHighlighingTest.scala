package org.jetbrains.plugins.scala.projectHighlighting

import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

/**
  * Nikolay.Tropin
  * 01-Aug-17
  */
@Category(Array(classOf[HighlightingTests]))
class BetterFilesProjectHighlighingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "pathikrit"

  override def githubRepoName = "better-files"

  //v.3.0.0
  override def revision = "eb7a357713c083534de9eeaee771750582c8ad31"
}
