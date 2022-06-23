package org.jetbrains.plugins.scala.projectHighlighting

import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class LaminarProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "raquo"
  override def githubRepoName = "laminar"

  //0.14.2
  override def revision = "69063beb52891f8a5d587648cb52607af198e177"

  import org.jetbrains.plugins.scala.projectHighlighting.ImplicitConversions.tupleToTextRange

  override def filesWithProblems = Map(
    "src/main/scala/com/raquo/laminar/api/Laminar.scala" -> Set((13351, 13355))
  )
}
