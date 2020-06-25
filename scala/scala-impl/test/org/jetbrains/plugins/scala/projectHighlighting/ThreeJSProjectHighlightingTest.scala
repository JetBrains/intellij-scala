package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class ThreeJSProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  override def jdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_8

  override def githubUsername = "antonkulaga"

  override def githubRepoName = "threejs-facade"

  override def revision = "d8417746908daaaac14436b486123ec284d67cdd"

  override def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "preview/backend/src/main/scala/org/denigma/preview/Routes.scala" -> Set()
  )
}
