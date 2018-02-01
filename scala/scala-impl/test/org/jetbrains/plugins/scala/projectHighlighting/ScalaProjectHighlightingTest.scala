//package org.jetbrains.plugins.scala.projectHighlighting
//
//import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
//import com.intellij.openapi.util.TextRange
//import org.jetbrains.plugins.scala.HighlightingTests
//import org.jetbrains.sbt.project.settings.SbtProjectSettings
//import org.junit.experimental.categories.Category
//
//@Category(Array(classOf[HighlightingTests]))
//class ScalaProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
//  override def githubUsername = "scala"
//
//  override def githubRepoName = "scala"
//
//  override def revision = "8e6964a13035bf83d3050916e988715d23e51b49"
//
//  override def filesWithProblems(): Map[String, Set[TextRange]] = Map(
//  )
//
//  override def getCurrentExternalProjectSettings: ExternalProjectSettings = {
//    super.getCurrentExternalProjectSettings match {
//      case sbt: SbtProjectSettings =>
//        sbt.useSbtShell = true
//        sbt
//      case other => other
//    }
//  }
//}
