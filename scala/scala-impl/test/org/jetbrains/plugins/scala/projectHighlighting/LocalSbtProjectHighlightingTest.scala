package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.performance.ImportingProjectTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.experimental.categories.Category

abstract class LocalSbtProjectHighlightingTest extends ImportingProjectTestCase with AllProjectHighlightingTest {
  override def getProject: Project = myProject

  override def rootProjectsDirPath = s"${TestUtils.getTestDataPath}/localProjects"

  override def getProjectFixture = codeInsightFixture

  def testHighlighting(): Unit = doAllProjectHighlightingTest()
}

//TODO: some sources in testData/localProjects folder are commented due to bug in Java highlighting (see IDEA-300681)
// when it's fixed, please uncomment test data (all commented code has issue id in the beginning)
@Category(Array(classOf[HighlightingTests]))
class AkkaSamplesTest extends LocalSbtProjectHighlightingTest {

  override def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_8

  override def projectName = "akka-samples"
}

@Category(Array(classOf[HighlightingTests]))
class ScalaPetStoreTest extends LocalSbtProjectHighlightingTest {

  override def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_8

  override def projectName = "scala-pet-store"
}
