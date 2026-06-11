package org.jetbrains.sbt.project

import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.project.ProjectStructureTestUtils.checkDisplayModuleNames
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests2]))
final class DisplayModuleNamesTest extends SbtProjectStructureImportingTestBase {

  def testMultiBuildProjectWithTheSameProjectIdsInBuilds(): Unit = {
    importProject(false)
    val expectedDisplayModuleNames = Map(
      "root" -> "root",
      "root.main" -> "root.main",
      "root.test" -> "root.test",
      "root.dummy" -> "dummy",
      "root.dummy.main" -> "dummy.main",
      "root.dummy.test" -> "dummy.test",
      "c1.root" -> "root",
      "c1.root.main" -> "root.main",
      "c1.root.test" -> "root.test",
      "c1.root.dummy" -> "dummy",
      "c1.root.dummy.test" -> "dummy.test",
      "c1.root.dummy.main" -> "dummy.main",
    )
    checkDisplayModuleNames(getMyProject, expectedDisplayModuleNames)
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
    checkDisplayModuleNames(getMyProject, expectedDisplayModuleNames)
  }

}
