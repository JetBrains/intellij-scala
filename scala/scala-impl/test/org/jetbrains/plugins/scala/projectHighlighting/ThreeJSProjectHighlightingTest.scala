package org.jetbrains.plugins.scala.projectHighlighting

import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class ThreeJSProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "antonkulaga"

  override def githubRepoName = "threejs-facade"

  override def revision = "d8417746908daaaac14436b486123ec284d67cdd"

  override def filesWithProblems: Map[String, Seq[(Int, Int)]] = Map(
    "Routes.scala" -> Seq(),
    "ThreeJSExample.scala" -> Seq((1242, 1295),(1242, 1295),(1220, 1239))
  )
}
