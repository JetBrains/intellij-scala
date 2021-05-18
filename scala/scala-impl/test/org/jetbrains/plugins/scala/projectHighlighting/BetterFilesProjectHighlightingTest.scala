package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.FlakyTests
import org.junit.experimental.categories.Category

/**
  * Nikolay.Tropin
  * 01-Aug-17
  */
@Category(Array(classOf[FlakyTests]))
class BetterFilesProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  override def jdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_8

  override def githubUsername = "pathikrit"

  override def githubRepoName = "better-files"

  //v.3.0.0
  override def revision = "eb7a357713c083534de9eeaee771750582c8ad31"
}
