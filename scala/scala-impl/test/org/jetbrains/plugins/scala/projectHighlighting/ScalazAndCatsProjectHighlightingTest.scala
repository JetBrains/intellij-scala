package org.jetbrains.plugins.scala.projectHighlighting

import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.performance.GithubRepositoryWithRevision
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class ScalazAndCatsProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("fosskers", "scalaz-and-cats", "e35a79297e06fafa7c76dda4bd74862131f2d37b")

  override def projectDirPath = s"$rootProjectsDirPath/$projectName/scala"
}
