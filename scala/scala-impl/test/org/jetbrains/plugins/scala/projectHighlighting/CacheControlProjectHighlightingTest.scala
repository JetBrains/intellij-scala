package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.performance.GithubRepositoryWithRevision
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class CacheControlProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  override def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_8

  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("playframework", "cachecontrol", "d46b3ff25e1e881ff037bec52664969807edf2a6")
}
