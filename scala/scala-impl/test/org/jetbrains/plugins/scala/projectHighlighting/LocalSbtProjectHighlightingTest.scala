package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.performance.ImportingProjectTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Ignore
import org.junit.experimental.categories.Category

abstract class LocalSbtProjectHighlightingTest extends ImportingProjectTestCase with AllProjectHighlightingTest {
  override def getProject: Project = myProject

  override def rootDirPath = s"${TestUtils.getTestDataPath}/localProjects"

  def testHighlighting(): Unit = doAllProjectHighlightingTest()

  override def getProjectFixture = codeInsightFixture
}

// FIXME: the errors come from Java PSI but we may also be the ones to blame
@Ignore("poly expression evaluation during overload resolution, processing 1 results")
@Category(Array(classOf[HighlightingTests]))
class AkkaSamplesTest extends LocalSbtProjectHighlightingTest {

  override def jdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_8

  override def projectName = "akka-samples"
}

@Category(Array(classOf[HighlightingTests]))
@Ignore
class ScalaPetStoreTest extends LocalSbtProjectHighlightingTest {
  override def projectName = "scala-pet-store"
}
