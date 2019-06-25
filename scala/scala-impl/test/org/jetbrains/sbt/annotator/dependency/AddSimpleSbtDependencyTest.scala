package org.jetbrains.sbt
package annotator
package dependency

import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.junit.Assert._
import org.junit.experimental.categories.Category

/**
 * Created by afonichkin on 8/29/17.
 */
@Category(Array(classOf[TypecheckerTests]))
class AddSimpleSbtDependencyTest extends AnnotatorTestBase {

  import AddSbtDependencyUtils._
  import AddSimpleSbtDependencyTest._

  override def testdataPath: String = s"${super.testdataPath}/dependency"

  def testSingleLibraryDependency(): Unit = {
    val testFile = loadTestFile()
    val topLevelDependencies = getTopLevelLibraryDependencies(testFile)
    assertEquals(1, topLevelDependencies.length)

    val dep = topLevelDependencies.head
    assertFalse(dep.right.isInstanceOf[ScMethodCall])

    addDependency(dep, FakeArtifact_1)(myProject)
    assertTrue(dep.right.isInstanceOf[ScMethodCall])
    val call = dep.right.asInstanceOf[ScMethodCall]

    assertTrue(toArtifactInfos(call).contains(FakeArtifact_1))

    addDependency(dep, FakeArtifact_2)(myProject)
    assertTrue(toArtifactInfos(call).contains(FakeArtifact_2))
  }

  def testMultipleLibraryDependencies(): Unit = {
    val testFile = loadTestFile()
    val topLevelDependencies = getTopLevelLibraryDependencies(testFile)

    assertEquals(2, topLevelDependencies.length)
  }
}

object AddSimpleSbtDependencyTest {

  import resolvers.ArtifactInfo

  private val FakeArtifact_1 = ArtifactInfo("xx", "yy", "1.0")
  private val FakeArtifact_2 = ArtifactInfo("xx", "yy", "2.0")

  private def toArtifactInfos(call: ScMethodCall) =
    call.argumentExpressions.map {
      _.getText.replaceAll("\"", "").split("%").map(_.trim)
    }.map { strings =>
      val Array(groupId, artifactId, versionId) = strings
      ArtifactInfo(groupId, artifactId, versionId)
    }
}
