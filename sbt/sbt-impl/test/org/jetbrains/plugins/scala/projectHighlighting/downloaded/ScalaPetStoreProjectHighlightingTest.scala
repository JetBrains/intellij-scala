package org.jetbrains.plugins.scala.projectHighlighting.downloaded

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.projectHighlighting.base.{GithubRepositoryWithRevision, SbtProjectHighlightingLocalProjectsTestBase}

class ScalaPetStoreProjectHighlightingTest  extends GithubSbtAllProjectHighlightingTest  {

  override def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_8

  def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("pauljamescleary", "scala-pet-store", "a4391027771146daaa4b5a6599d36e6462d645b3")
}
