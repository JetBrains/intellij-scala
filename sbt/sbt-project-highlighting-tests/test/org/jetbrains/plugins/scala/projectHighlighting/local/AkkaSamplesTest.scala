package org.jetbrains.plugins.scala.projectHighlighting.local

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.projectHighlighting.base.SbtProjectHighlightingLocalProjectsTestBase

//TODO: see IDEA-300681
// some sources in testData/localProjects folder are commented due to bug in Java highlighting
// when it's fixed, please uncomment test data (all commented code has issue id in the beginning)
class AkkaSamplesTest extends SbtProjectHighlightingLocalProjectsTestBase {

  override def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_8

  override def projectName = "akka-samples"
}
