package org.jetbrains.sbt.project

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.sbt.project.ProjectStructureTestUtils.checkDisplayModuleNames
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
final class DisplayModuleNamesTest extends SbtProjectStructureImportingLike {

  override protected def enableSeparateModulesForProdTest: Boolean = false

  def testMultiBuildProjectWithTheSameProjectIdsInBuilds(): Unit = {
    importProject(false)
    val expectedDisplayModuleNames = Map(
      "root" -> "root",
      "root.dummy" -> "dummy",
      "root~1" -> "root~1",
      "root~1.dummy" -> "dummy",
    )
    checkDisplayModuleNames(myProject, expectedDisplayModuleNames)
  }

  def testMultiModule(): Unit = {
    importProject(false)
    val expectedDisplayModuleNames = Map(
      "multiModule" -> "multiModule",
      "multiModule.bar" -> "bar",
      "multiModule.foo" -> "foo",
    )
    checkDisplayModuleNames(myProject, expectedDisplayModuleNames)
  }

}
