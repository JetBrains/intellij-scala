package org.jetbrains.plugins.scala.projectHighlighting.downloaded

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.projectHighlighting.base.GithubRepositoryWithRevision

class LaminarProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  //version 0.14.2
  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("raquo", "laminar", "69063beb52891f8a5d587648cb52607af198e177")

  override def filesWithProblems: Map[String, Set[TextRange]] = Map()
}
