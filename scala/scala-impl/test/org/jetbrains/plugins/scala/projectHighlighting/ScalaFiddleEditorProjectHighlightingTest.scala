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
    "client/src/main/scala/scalafiddle/client/component/FiddleEditor.scala" -> Set((14771, 14778)),
    "server/src/main/scala/controllers/SocialAuthController.scala" -> Set(),
    "server/src/main/scala/controllers/Application.scala" -> Set(),
  )
}
