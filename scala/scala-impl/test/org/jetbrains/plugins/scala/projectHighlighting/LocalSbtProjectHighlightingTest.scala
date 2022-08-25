package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.performance.{DownloadingAndImportingTestCase, ImportingProjectTestCase}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.experimental.categories.Category

abstract class LocalSbtProjectHighlightingTest extends ImportingProjectTestCase with AllProjectHighlightingTest {
  override def getProject: Project = myProject

  override def rootProjectsDirPath: String = s"${TestUtils.getTestDataPath}/localProjects"

  //reuse same ivy caches used in DownloadingAndImportingTestCase
  override def ivyAndCoursierCachesRootPath: String = DownloadingAndImportingTestCase.rootProjectsDirPath

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
