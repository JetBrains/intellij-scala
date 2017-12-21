package org.jetbrains.plugins.scala.projectHighlighting

import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class ScalaPetStoreProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "pauljamescleary"

  override def githubRepoName = "scala-pet-store"

  override def revision = "fffc2f5f0797aee254dbd53c72053ac836c59caa"

  override def filesWithProblems: Map[String, Seq[(Int, Int)]] = Map(
  )
}
