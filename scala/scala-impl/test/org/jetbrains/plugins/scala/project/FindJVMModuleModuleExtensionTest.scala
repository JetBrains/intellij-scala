package org.jetbrains.plugins.scala.project

import com.intellij.openapi.module.Module
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.junit.Assert.{assertNotNull, assertTrue}
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
class FindJVMModuleModuleExtensionTest extends SbtExternalSystemImportingTestLike {

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/${getTestName(true)}"

  override def setUp(): Unit = {
    super.setUp()
    importProject(false)
  }

  def testCrossPlatformWithNestedProjectDependencies(): Unit = {
    val sharedSourcesModules = findAllSharedSourcesModules(numberOfRequiredModules = 6)

    val module1SharedMain = sharedSourcesModules.find(_.getName == "crossPlatformWithNestedProjectDependencies.module1.module1-sources.main").orNull
    val module1SharedTest = sharedSourcesModules.find(_.getName == "crossPlatformWithNestedProjectDependencies.module1.module1-sources.test").orNull
    val module2SharedMain = sharedSourcesModules.find(_.getName == "crossPlatformWithNestedProjectDependencies.module2.module2-sources.main").orNull
    val module2SharedTest = sharedSourcesModules.find(_.getName == "crossPlatformWithNestedProjectDependencies.module2.module2-sources.test").orNull
    val pairs = Seq(
      (module1SharedMain, "module1.module1JVM.main"),
      (module1SharedTest, "module1.module1JVM.test"),
      (module2SharedMain, "module2.module2JVM.main"),
      (module2SharedTest, "module2.module2JVM.test"),
    )

    pairs.foreach { case (module, jvmModuleName) =>
      assertNotNull(module)
      val jvmModule = module.findJVMModule.orNull
      val targetModuleName = s"crossPlatformWithNestedProjectDependencies.$jvmModuleName"
      jvmModuleAssertions(jvmModule, module.getName)(_ == targetModuleName)
    }
  }

  def testSharedSources(): Unit = {
    val sharedSourcesModules = findAllSharedSourcesModules(numberOfRequiredModules = 2)
    val sharedSourcesModule = sharedSourcesModules.find(_.getName == "sharedSourcesProject.sharedSources-sources.main").orNull
    assertNotNull(sharedSourcesModule)
    val jvmModule = sharedSourcesModule.findJVMModule.orNull

    // this test create shared sources from unmanagedSourceDirectories.
    // In such case the a module can be selected from any module that owns a given shared source
    jvmModuleAssertions(jvmModule, sharedSourcesModule.getName) { foundName =>
      foundName == "sharedSourcesProject.bar.main" || foundName == "sharedSourcesProject.foo.main"
    }
  }

  private def findAllSharedSourcesModules(numberOfRequiredModules: Int): Seq[Module] = {
    val modules = this.getMyTestFixture.getProject.modules
    val sharedSourcesModules = modules.filter(_.getModuleTypeName == "SHARED_SOURCES_MODULE")
    assertTrue(s"There should be $numberOfRequiredModules shared sources modules but there were ${sharedSourcesModules.size}", sharedSourcesModules.size == numberOfRequiredModules)
    sharedSourcesModules
  }

  private def jvmModuleAssertions(@Nullable jvmModule: Module, sharedSourcesModuleName: String)(isCorrectModuleName: String => Boolean): Unit = {
    assertTrue(s"JVM module not found for $sharedSourcesModuleName", jvmModule != null)
    val jvmModuleName = jvmModule.getName
    assertTrue(s"JVM module for module $sharedSourcesModuleName found with an invalid name", isCorrectModuleName(jvmModuleName))
  }
}
