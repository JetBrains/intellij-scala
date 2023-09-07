package org.jetbrains.plugins.scala.projectHighlighting.downloaded

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.projectHighlighting.base.GithubRepositoryWithRevision

class CacheControlProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  override def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_8

  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("playframework", "cachecontrol", "2.2.0")
}
