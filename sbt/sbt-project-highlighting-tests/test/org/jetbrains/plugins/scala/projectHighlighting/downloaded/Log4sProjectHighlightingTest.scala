package org.jetbrains.plugins.scala.projectHighlighting.downloaded

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.projectHighlighting.base.GithubRepositoryWithRevision

class Log4sProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  // 1.11.0-RC1
  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("Log4s", "log4s", "fa60d4b6abf91dd3625ee29a57f4dc2d41b27602")

  import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

  override def filesWithProblems: Map[String, Set[TextRange]] = Map(
    // The JS module defines its own `org.slf4j.MDC` object, while the Java `org.slf4j.MDC` from slf4j-api is on the
    // same classpath. The compiler prefers the source definition, but `MDC` resolves to the library class here.
    "core/js/src/main/scala/org/log4s/log4sjs/Log4sLoggerFactory.scala" -> Set(
      (723, 730), // Cannot resolve symbol asScala
    ),
  )
}
