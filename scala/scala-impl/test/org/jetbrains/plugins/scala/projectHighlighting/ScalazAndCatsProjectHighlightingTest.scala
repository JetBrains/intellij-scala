package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class ScalazAndCatsProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "fosskers"

  override def githubRepoName = "scalaz-and-cats"

  override def revision = "e35a79297e06fafa7c76dda4bd74862131f2d37b"

  override def projectDirPath = s"$rootDirPath/$projectName/scala"

  override def filesWithProblems: Map[String, Seq[(Int, Int)]] = Map(
    "Kitties.scala" -> Seq(),
    "Zed.scala" -> Seq()
  )
}
