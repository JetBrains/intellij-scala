package org.jetbrains.sbt.project

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.sbt.project.ProjectStructureTestUtils.checkDisplayModuleNames
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
final class DisplayModuleNamesTest_ProdTestSourcesSeparated extends SbtProjectStructureImportingLike {

  def testMultiBuildProjectWithTheSameProjectIdsInBuilds(): Unit = {
    importProject(false)
    val expectedDisplayModuleNames = Map(
      "root" -> "root",
      "root.main" -> "root.main",
      "root.test" -> "root.test",
      "root.dummy" -> "dummy",
      "root.dummy.main" -> "dummy.main",
      "root.dummy.test" -> "dummy.test",
      "root~1" -> "root~1",
      "root~1.main" -> "root~1.main",
      "root~1.test" -> "root~1.test",
      "root~1.dummy" -> "dummy",
      "root~1.dummy.test" -> "dummy.test",
      "root~1.dummy.main" -> "dummy.main",
    )
    checkDisplayModuleNames(myProject, expectedDisplayModuleNames)
  }

  def testMultiModule(): Unit = {
    importProject(false)
    val expectedDisplayModuleNames = Map(
      "multiModule" -> "multiModule",
      "multiModule.main" -> "multiModule.main",
      "multiModule.test" -> "multiModule.test",
      "multiModule.bar" -> "bar",
      "multiModule.bar.main" -> "bar.main",
      "multiModule.bar.test" -> "bar.test",
      "multiModule.foo" -> "foo",
      "multiModule.foo.main" -> "foo.main",
      "multiModule.foo.test" -> "foo.test",
    )
    checkDisplayModuleNames(myProject, expectedDisplayModuleNames)
  }

}
