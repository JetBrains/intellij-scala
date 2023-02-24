package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.projectHighlighting.base.GithubRepositoryWithRevision
import org.jetbrains.plugins.scala.projectHighlighting.downloaded.GithubSbtAllProjectHighlightingTest
import org.junit.Ignore
import org.junit.experimental.categories.Category

//TODO: fix errors in scala project and ignore the test
@Ignore
@Category(Array(classOf[HighlightingTests]))
class ScalaRepositoryProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("scala", "scala", "2.13.x")

  override protected def filesWithProblems: Map[String, Set[TextRange]] = Map(
  )
}
