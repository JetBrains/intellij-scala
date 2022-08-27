package org.jetbrains.plugins.scala.projectHighlighting.downloaded

import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.projectHighlighting.base.GithubRepositoryWithRevision
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class LaminarProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  //version 0.14.2
  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("raquo", "laminar", "69063beb52891f8a5d587648cb52607af198e177")

  import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

  override def filesWithProblems = Map(
    "src/main/scala/com/raquo/laminar/api/Laminar.scala" -> Set((13351, 13355))
  )
}
