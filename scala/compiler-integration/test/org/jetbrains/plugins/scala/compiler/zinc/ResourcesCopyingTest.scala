package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.roots.{CompilerModuleExtension, ModuleRootManager}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.{CompilerTester, VfsTestUtil}
import junit.framework.TestCase.{assertNotNull, assertTrue}
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.experimental.categories.Category

import java.nio.file.Files
import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
abstract class ResourcesCopyingTestBase(
  separateMainTest: Boolean,
  incrementalityType: IncrementalityType,
  expectedResourcesCopied: Boolean
) extends ZincTestBase(separateProdAndTestSources = separateMainTest) {

  private var apiMainModule: Module = _
  private var apiTestModule: Module = _
  private var middleMainModule: Module = _
  private var middleTestModule: Module = _
  private var implMainModule: Module = _
  private var implTestModule: Module = _

  override def setUp(): Unit = {
    super.setUp()

    createProjectSubDirs(
      "project",
      "api/src/main/scala", "api/src/main/resources", "api/src/test/scala", "api/src/test/resources",
      "middle/src/main/scala", "middle/src/main/resources", "middle/src/test/scala", "middle/src/test/resources",
      "impl/src/main/scala", "impl/src/main/resources", "impl/src/test/scala", "impl/src/test/resources"
    )
    createProjectSubFile("project/build.properties", "sbt.version=1.10.6")

    createProjectSubFile("api/src/main/scala/Api.scala", "trait Api")
    createProjectSubFile("api/src/main/resources/api1.txt", "api1")
    createProjectSubFile("api/src/main/resources/api2.txt", "api2")
    createProjectSubFile("api/src/main/resources/api3.txt", "api3")
    createProjectSubFile("api/src/test/scala/ApiTest.scala", "class ApiTest extends Api")
    createProjectSubFile("api/src/test/resources/test-api1.txt", "test-api1")
    createProjectSubFile("api/src/test/resources/test-api2.txt", "test-api2")
    createProjectSubFile("api/src/test/resources/test-api3.txt", "test-api3")

    createProjectSubFile("middle/src/main/scala/AbstractApi.scala", "abstract class AbstractApi extends Api")
    createProjectSubFile("middle/src/main/resources/abstract1.txt", "abstract1")
    createProjectSubFile("middle/src/main/resources/abstract2.txt", "abstract2")
    createProjectSubFile("middle/src/main/resources/abstract3.txt", "abstract3")
    createProjectSubFile("middle/src/test/scala/AbstractApiTest.scala", "class AbstractApiTest extends AbstractApi")
    createProjectSubFile("middle/src/test/resources/test-abstract1.txt", "test-abstract1")
    createProjectSubFile("middle/src/test/resources/test-abstract2.txt", "test-abstract2")
    createProjectSubFile("middle/src/test/resources/test-abstract3.txt", "test-abstract3")

    createProjectSubFile("impl/src/main/scala/ApiImpl.scala", "class ApiImpl extends AbstractApi")
    createProjectSubFile("impl/src/main/resources/impl1.txt", "impl1")
    createProjectSubFile("impl/src/main/resources/impl2.txt", "impl2")
    createProjectSubFile("impl/src/main/resources/impl3.txt", "impl3")
    createProjectSubFile("impl/src/test/scala/ApiImplTest.scala", "class ApiImplTest")
    createProjectSubFile("impl/src/test/resources/test-impl1.txt", "test-impl1")
    createProjectSubFile("impl/src/test/resources/test-impl2.txt", "test-impl2")
    createProjectSubFile("impl/src/test/resources/test-impl3.txt", "test-impl3")

    createProjectConfig(
      """ThisBuild / scalaVersion := "3.6.2"
        |lazy val api = project.in(file("api"))
        |lazy val middle = project.in(file("middle")).dependsOn(api)
        |lazy val impl = project.in(file("impl")).dependsOn(middle)
        |lazy val root = project.in(file(".")).aggregate(api, middle, impl)
        |""".stripMargin)

    importProject(false)
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = incrementalityType

    val modules = ModuleManager.getInstance(myProject).getModules
    rootModule = modules.find(_.getName == "root").orNull
    assertNotNull("Could not find module with name 'root'", rootModule)

    apiMainModule = findMainModule(modules, "api")
    apiTestModule = findTestModule(modules, "api")
    middleMainModule = findMainModule(modules, "middle")
    middleTestModule = findTestModule(modules, "middle")
    implMainModule = findMainModule(modules, "impl")
    implTestModule = findTestModule(modules, "impl")

    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)
  }

  def testResourcesAreCopied(): Unit = {
    val messages1 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages1)
    assertClassesCompiled()
    assertResourcesCopied()

    removeAllOutputDirectories()

    if (expectedResourcesCopied) {
      val messages2 = compiler.make().asScala.toSeq
      assertNoErrorsOrWarnings(messages2)
      assertClassesCompiled()
      assertResourcesCopied()
    }
  }

  private def findMainModule(modules: Array[Module], name: String): Module = {
    val moduleName = if (separateMainTest) s"root.$name.main" else s"root.$name"
    val m = modules.find(_.getName == moduleName).orNull
    assertNotNull(s"Could not find module with name '$moduleName'", m)
    m
  }

  private def findTestModule(modules: Array[Module], name: String): Module = {
    val moduleName = if (separateMainTest) s"root.$name.test" else s"root.$name"
    val m = modules.find(_.getName == moduleName).orNull
    assertNotNull(s"Could not find module with name '$moduleName'", m)
    m
  }

  private def assertClassesCompiled(): Unit = {
    assertCompiledClass(apiMainModule, "Api")
    assertCompiledTestClass(apiTestModule, "ApiTest")
    assertCompiledClass(middleMainModule, "AbstractApi")
    assertCompiledTestClass(middleTestModule, "AbstractApiTest")
    assertCompiledClass(implMainModule, "ApiImpl")
    assertCompiledTestClass(implTestModule, "ApiImplTest")
  }

  private def assertResourcesCopied(): Unit = {
    assertResourceCopied(apiMainModule, "api1.txt", prod = true)
    assertResourceCopied(apiMainModule, "api2.txt", prod = true)
    assertResourceCopied(apiMainModule, "api3.txt", prod = true)
    assertResourceCopied(apiTestModule, "test-api1.txt", prod = false)
    assertResourceCopied(apiTestModule, "test-api2.txt", prod = false)
    assertResourceCopied(apiTestModule, "test-api3.txt", prod = false)
    assertResourceCopied(middleMainModule, "abstract1.txt", prod = true)
    assertResourceCopied(middleMainModule, "abstract2.txt", prod = true)
    assertResourceCopied(middleMainModule, "abstract3.txt", prod = true)
    assertResourceCopied(middleTestModule, "test-abstract1.txt", prod = false)
    assertResourceCopied(middleTestModule, "test-abstract2.txt", prod = false)
    assertResourceCopied(middleTestModule, "test-abstract3.txt", prod = false)
    assertResourceCopied(implMainModule, "impl1.txt", prod = true)
    assertResourceCopied(implMainModule, "impl2.txt", prod = true)
    assertResourceCopied(implMainModule, "impl3.txt", prod = true)
    assertResourceCopied(implTestModule, "test-impl1.txt", prod = false)
    assertResourceCopied(implTestModule, "test-impl2.txt", prod = false)
    assertResourceCopied(implTestModule, "test-impl3.txt", prod = false)
  }

  private def removeAllOutputDirectories(): Unit = {
    def outs(module: Module): Seq[VirtualFile] = {
      val extension = ModuleRootManager.getInstance(module).getModuleExtension(classOf[CompilerModuleExtension])
      Seq(Option(extension.getCompilerOutputPath), Option(extension.getCompilerOutputPathForTests)).flatten
    }
    Seq(apiMainModule, apiTestModule, middleMainModule, middleTestModule, implMainModule, implTestModule)
      .flatMap(outs).foreach(_.getChildren.foreach(VfsTestUtil.deleteFile))
  }

  private def assertResourceCopied(module: Module, name: String, prod: Boolean): Unit = {
    val extension = ModuleRootManager.getInstance(module).getModuleExtension(classOf[CompilerModuleExtension])
    val out = if (prod) extension.getCompilerOutputPath else extension.getCompilerOutputPathForTests
    assertNotNull(out)
    val resource = out.toNioPath.resolve(name)
    assertTrue(s"Resource file $name was not copied", Files.exists(resource))
  }

  private def assertCompiledClass(module: Module, name: String): Unit = {
    findClassFile(module, name)
  }

  private def assertCompiledTestClass(module: Module, name: String): Unit = {
    val out = ModuleRootManager.getInstance(module)
      .getModuleExtension(classOf[CompilerModuleExtension]).getCompilerOutputPathForTests
    assertNotNull(out)
    val expectedName = name.replace('.', '/')
    val classFile = out.toNioPath.resolve(s"$expectedName.class")
    assertTrue(s"Test class file $expectedName.class was not compiled", Files.exists(classFile))
  }
}

class ResourcesCopyingTest_Zinc
  extends ResourcesCopyingTestBase(separateMainTest = false, IncrementalityType.SBT, expectedResourcesCopied = true)

class ResourcesCopyingTest_Zinc_Split
  extends ResourcesCopyingTestBase(separateMainTest = true, IncrementalityType.SBT, expectedResourcesCopied = true)

class ResourcesCopyingTest_IDEA
  extends ResourcesCopyingTestBase(separateMainTest = false, IncrementalityType.IDEA, expectedResourcesCopied = false)

class ResourcesCopyingTest_IDEA_Split
  extends ResourcesCopyingTestBase(separateMainTest = true, IncrementalityType.IDEA, expectedResourcesCopied = false)
