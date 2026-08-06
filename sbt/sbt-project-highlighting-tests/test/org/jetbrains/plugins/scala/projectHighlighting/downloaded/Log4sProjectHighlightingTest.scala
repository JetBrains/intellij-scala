package org.jetbrains.plugins.scala.projectHighlighting.downloaded

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.projectHighlighting.base.GithubRepositoryWithRevision

class Log4sProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("Log4s", "log4s", "8439aef843da2c9f489d1dff4cf62df6135fb9d8")

  override def filesWithProblems: Map[String, Set[TextRange]] = Map(
  )
}
