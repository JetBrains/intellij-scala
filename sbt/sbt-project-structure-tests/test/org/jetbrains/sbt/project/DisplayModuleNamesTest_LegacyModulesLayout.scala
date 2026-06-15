package org.jetbrains.sbt.project

import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.project.ProjectStructureTestUtils.checkDisplayModuleNames
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests2]))
final class DisplayModuleNamesTest_LegacyModulesLayout extends SbtProjectStructureImportingTestBase {

  override protected def getTestSbtProjectSettings =
    super.getTestSbtProjectSettings.copy(separateProdAndTestSources = false)

  def testMultiBuildProjectWithTheSameProjectIdsInBuilds(): Unit = {
    importProject(false)
    val expectedDisplayModuleNames = Map(
      "root" -> "root",
      "root.dummy" -> "dummy",
      "c1.root" -> "root",
      "c1.root.dummy" -> "dummy",
    )
    checkDisplayModuleNames(getMyProject, expectedDisplayModuleNames)
  }

  def testMultiModule(): Unit = {
    importProject(false)
    val expectedDisplayModuleNames = Map(
      "multiModule" -> "multiModule",
      "multiModule.bar" -> "bar",
      "multiModule.foo" -> "foo",
    )
    checkDisplayModuleNames(getMyProject, expectedDisplayModuleNames)
  }

}
