package org.jetbrains.plugins.scala.annotator.template.intention.sbt

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.sbt.annotator.dependency.AddSbtDependencyUtils
import org.jetbrains.sbt.language.SbtFileImpl
import org.jetbrains.sbt.resolvers.ArtifactInfo

/**
  * Created by afonichkin on 8/29/17.
  */
class AddSimpleSbtDependencyTest extends AddSbtDependencyTestBase {
  val FAKE_ARTIFACT_1 = ArtifactInfo("xx", "yy", "1.0")
  val FAKE_ARTIFACT_2 = ArtifactInfo("xx", "yy", "2.0")

  def testAddDependenciesToSingleDependency(): Unit = {
    val testFile: SbtFileImpl = loadTestFile("singleLibraryDependency.sbt")
    val topLevelDependencies = AddSbtDependencyUtils.getTopLevelLibraryDependencies(testFile)
    assert(topLevelDependencies.length == 1)

    val dep = topLevelDependencies.head
    assert(!dep.right.isInstanceOf[ScMethodCall])

    AddSbtDependencyUtils.addDependency(dep, FAKE_ARTIFACT_1)(myProject)
    assert(dep.right.isInstanceOf[ScMethodCall])
    val call = dep.right.asInstanceOf[ScMethodCall]

    assert(call.args.exprsArray.map(t => toArtifactInfo(t.getText)).contains(FAKE_ARTIFACT_1))

    AddSbtDependencyUtils.addDependency(dep, FAKE_ARTIFACT_2)(myProject)
    assert(call.args.exprsArray.map(t => toArtifactInfo(t.getText)).contains(FAKE_ARTIFACT_2))
  }

  def testGetMultipleDependencies(): Unit = {
    val testFile: SbtFileImpl = loadTestFile("multipleLibraryDependencies.sbt")
    val topLevelDependencies = AddSbtDependencyUtils.getTopLevelLibraryDependencies(testFile)

    assert(topLevelDependencies.length == 2)
  }
}
