package org.jetbrains.plugins.scala.projectHighlighting.downloaded

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.projectHighlighting.base.GithubRepositoryWithRevision

class BetterFilesProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  override def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_17

  //v.3.9.2
  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("pathikrit", "better-files", "6f2e3f1328b1b18eddce973510db71bc6c14fadb")
}
