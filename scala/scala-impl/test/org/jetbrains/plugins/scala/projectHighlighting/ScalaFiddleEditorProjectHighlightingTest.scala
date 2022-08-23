package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class ScalaFiddleEditorProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  override def jdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_8

  override def githubUsername = "scalafiddle"

  override def githubRepoName = "scalafiddle-editor"

  override def revision = "e9bbda4d4190d262a93405365f38c93c8e7988b5"

  override def filesWithProblems: Map[String, Set[TextRange]] = Map(
    //these two files contain unresolved references to generated routes sources (e.g. routes.Application)
    //in order there are no errors, the project should be not just imported but compiled (probably with sbt shell)
    "server/src/main/scala/controllers/SocialAuthController.scala" -> Set(),
    "server/src/main/scala/controllers/Application.scala" -> Set(),

    //mute SCL-20532
    "server/src/main/scala/scalafiddle/server/Librarian.scala" -> Set()
  )
}
