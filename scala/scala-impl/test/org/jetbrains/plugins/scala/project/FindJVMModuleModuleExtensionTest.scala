package org.jetbrains.plugins.scala.project

import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.junit.Assert.{assertNotNull, assertTrue}
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.Nullable

class FindJVMModuleModuleExtensionTest extends SbtExternalSystemImportingTestLike {

  override protected def getTestProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/${getTestName(true)}"

  override def setUp(): Unit = {
    super.setUp()
    importProject(false)
  }

  def testCrossPlatformWithNestedProjectDependencies(): Unit = {
    val sharedSourcesModules = findAllSharedSourcesModules(numberOfRequiredModules = 2)

    val module1Shared = sharedSourcesModules.find(_.getName == "crossPlatformWithNestedProjectDependencies.module1.module1-sources").orNull
    assertNotNull("Shared sources module for module1 is null", module1Shared)
    val module2Shared = sharedSourcesModules.find(_.getName == "crossPlatformWithNestedProjectDependencies.module2.module2-sources").orNull
    assertNotNull("Shared sources module for module2 is null", module2Shared)

    Seq((module2Shared, "module2.module2JVM"), (module1Shared, "module1.module1JVM")).foreach { case (module, jvmModuleName) =>
      val jvmModule = module.findJVMModule.orNull
      val targetModuleName = s"crossPlatformWithNestedProjectDependencies.$jvmModuleName"
      jvmModuleAssertions(jvmModule, module.getName)(_ == targetModuleName)
    }
  }

  def testSharedSources(): Unit = {
    val sharedSourcesModules = findAllSharedSourcesModules(numberOfRequiredModules = 1)
    val sharedSourcesModule = sharedSourcesModules.head
    val jvmModule = sharedSourcesModule.findJVMModule.orNull

    // this test create shared sources from unmanagedSourceDirectories.
    // In such case the a module can be selected from any module that owns a given shared source
    jvmModuleAssertions(jvmModule, sharedSourcesModule.getName) { foundName =>
      foundName == "sharedSourcesProject.bar" || foundName == "sharedSourcesProject.foo"
    }
  }

  private def findAllSharedSourcesModules(numberOfRequiredModules: Int): Seq[Module] = {
    val modules = this.myTestFixture.getProject.modules
    val sharedSourcesModules = modules.filter(_.getModuleTypeName == "SHARED_SOURCES_MODULE")
    assertTrue(s"There should be $numberOfRequiredModules shared sources modules", sharedSourcesModules.size == numberOfRequiredModules)
    sharedSourcesModules
  }

  private def jvmModuleAssertions(@Nullable jvmModule: Module, sharedSourcesModuleName: String)(isCorrectModuleName: String => Boolean): Unit = {
    assertTrue(s"JVM module not found for $sharedSourcesModuleName", jvmModule != null)
    val jvmModuleName = jvmModule.getName
    assertTrue(s"JVM module for module $sharedSourcesModuleName found with an invalid name", isCorrectModuleName(jvmModuleName))
  }
}