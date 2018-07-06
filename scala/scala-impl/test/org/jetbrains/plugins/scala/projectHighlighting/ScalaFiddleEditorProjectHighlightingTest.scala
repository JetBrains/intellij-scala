package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class ScalaFiddleEditorProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "scalafiddle"

  override def githubRepoName = "scalafiddle-editor"

  override def revision = "e9bbda4d4190d262a93405365f38c93c8e7988b5"

  override def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "AppRouter.scala" -> Set(),
    "FiddleEditor.scala" -> Set((14771, 14778)),
    "SocialAuthController.scala" -> Set(),
    "Application.scala" -> Set(),
    "Dropdown.scala" -> Set((161, 163),(801, 810),(798, 800))
  )
}
