package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.performance.ImportingProjectTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.experimental.categories.Category

/**
  * Nikolay.Tropin
  * 14-Dec-17
  */
abstract class LocalSbtProjectHighlightingTest extends ImportingProjectTestCase with AllProjectHighlightingTest {
  override def getProject: Project = myProject

  override def rootDirPath = s"${TestUtils.getTestDataPath}/localProjects"

  def testHighlighting(): Unit = doAllProjectHighlightingTest()

  override def getProjectFixture = codeInsightFixture
}

@Category(Array(classOf[HighlightingTests]))
class AkkaSamplesTest extends LocalSbtProjectHighlightingTest {
  override def projectName = "akka-samples"
}

@Category(Array(classOf[HighlightingTests]))
class ScalaPetStoreTest extends LocalSbtProjectHighlightingTest {
  override def projectName = "scala-pet-store"

  override def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "OrderEndointsSpec.scala" -> Set((824, 840)),
    "PetEndpoints.scala" -> Set(),
    "PetEndpointsSpec.scala" -> Set((911, 925),(1527, 1541)),
    "Arbitraries.scala" -> Set((574, 583))
  )
}
