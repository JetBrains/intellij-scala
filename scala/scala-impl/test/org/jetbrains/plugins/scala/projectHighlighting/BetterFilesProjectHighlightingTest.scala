package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class BetterFilesProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  //NOTE: doesn't work with JDK 11 due to bug in sbt 0.13.8 used in the this project at this revision
  //see https://github.com/sbt/sbt/issues/1593
  override def jdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_8

  override def githubUsername = "pathikrit"

  override def githubRepoName = "better-files"

  //v.3.0.0
  override def revision = "eb7a357713c083534de9eeaee771750582c8ad31"
}
