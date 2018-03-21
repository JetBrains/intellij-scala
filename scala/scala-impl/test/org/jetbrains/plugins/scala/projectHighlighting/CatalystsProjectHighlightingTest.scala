package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

/**
  * Nikolay.Tropin
  * 21-Mar-18
  */
@Category(Array(classOf[HighlightingTests]))
class CatalystsProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest  {

  override def githubUsername: String = "typelevel"

  override def githubRepoName: String = "catalysts"

  override def revision: String = "04aa6c7ac6ceb57cfbf7e698e8b683c11b50c842"

  override def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "MacroTests.scala" -> Set((132, 142)),
    "Prop.scala" -> Set(),
    "Laws.scala" -> Set((1269, 1274)),
    "AllTests.scala" -> Set((275, 292), (442, 458), (166, 183), (330, 346), (222, 238), (117, 131), (383, 402))
  )
}
