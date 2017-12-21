package org.jetbrains.plugins.scala.projectHighlighting

import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class ScalaFiddleEditorProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "scalafiddle"

  override def githubRepoName = "scalafiddle-editor"

  override def revision = "e9bbda4d4190d262a93405365f38c93c8e7988b5"

  override def filesWithProblems: Map[String, Seq[(Int, Int)]] = Map(
    "AppRouter.scala" -> Seq(),
    "FiddleEditor.scala" -> Seq(),
    "SocialAuthController.scala" -> Seq(),
    "Application.scala" -> Seq(),
    "Dropdown.scala" -> Seq((161, 163),(801, 810),(798, 800),(798, 800))
  )
}
