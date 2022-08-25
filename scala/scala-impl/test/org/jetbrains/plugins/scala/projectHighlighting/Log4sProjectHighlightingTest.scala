package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.performance.GithubRepositoryWithRevision
import org.junit.Ignore
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
@Ignore
class Log4sProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("Log4s", "log4s", "8439aef843da2c9f489d1dff4cf62df6135fb9d8")

  import org.jetbrains.plugins.scala.projectHighlighting.ImplicitConversions.tupleToTextRange

  override def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "src/test/scala/org/log4s/MDCSpec.scala" -> Set((356,374), (477,495), (904,922), (1049,1067), (1121,1147), (1173,1204), (1355,1373)),
  )
}
