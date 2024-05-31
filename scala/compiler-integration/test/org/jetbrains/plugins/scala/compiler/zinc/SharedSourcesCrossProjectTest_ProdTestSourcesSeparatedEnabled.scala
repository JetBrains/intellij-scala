package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.Assert.{assertNotNull, assertNull}
import org.junit.experimental.categories.Category

import java.io.File
import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
class SharedSourcesCrossProjectTest_ProdTestSourcesSeparatedEnabled extends ZincTestBase(separateProdAndTestSources = true) {

  private var middleJSMain: Module = _
  private var middleJSTest: Module = _
  private var middleJVMMain: Module = _
  private var middleJVMTest: Module = _
  private var middleSharedMain: Module = _
  private var middleSharedTest: Module = _

  private var baseJSMain: Module = _
  private var baseJSTest: Module = _
  private var baseJVMMain: Module = _
  private var baseJVMTest: Module = _
  private var baseSharedMain: Module = _
  private var baseSharedTest: Module = _

  override def setUp(): Unit = {
    super.setUp()

    createProjectSubDirs(
      "project",
      "base/js/src/main/scala", "base/jvm/src/main/scala", "base/shared/src/main/scala",
      "middle/js/src/main/scala", "middle/jvm/src/main/scala", "middle/shared/src/main/scala"
    )
    createProjectSubFile("project/build.properties", "sbt.version=1.9.7")
    createProjectSubFile("project/plugins.sbt",
    """addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.13.2")
      |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")
      |""".stripMargin)
    createProjectSubFile("base/jvm/src/main/scala/FooJVM.scala", "class FooJVM")
    createProjectSubFile("base/jvm/src/test/scala/FooJVMTest.scala", "class FooJVMTest")
    createProjectSubFile("base/js/src/main/scala/Foo.scala", "class FooJS")
    createProjectSubFile("base/js/src/test/scala/FooJSTest.scala", "class FooJSTest")
    createProjectSubFile("middle/shared/src/main/scala/Shared.scala", "class Shared")
    createProjectSubFile("middle/js/src/test/scala/MiddleJSTest.scala", "class MiddleJSTest")
    createProjectSubFile("middle/jvm/src/test/scala/MiddleJVMTest.scala", "class MiddleJVMTest")
    createProjectConfig(
      """lazy val root = project.in(file("."))
        |
        |lazy val middle = crossProject(JVMPlatform, JSPlatform).in(file("middle"))
        |
        |lazy val base = crossProject(JVMPlatform, JSPlatform).in(file("base"))
        |  .dependsOn(middle)
        |""".stripMargin)

    importProject(false)
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = IncrementalityType.SBT

    val modules = ModuleManager.getInstance(myProject).getModules
    middleJSMain = modules.find(_.getName == "root.middle.middleJS.main").orNull
    assertNotNull("Could not find module with name 'root.middle.middleJS.main'", middleJSMain)
    middleJSTest = modules.find(_.getName == "root.middle.middleJS.test").orNull
    assertNotNull("Could not find module with name 'root.middle.middleJS.test'", middleJSTest)
    middleJVMMain = modules.find(_.getName == "root.middle.middleJVM.main").orNull
    assertNotNull("Could not find module with name 'root.middle.middleJVM.main'", middleJVMMain)
    middleJVMTest = modules.find(_.getName == "root.middle.middleJVM.test").orNull
    assertNotNull("Could not find module with name 'root.middle.middleJVM.test'", middleJVMTest)
    middleSharedMain = modules.find(_.getName == "root.middle.middle-sources.main").orNull
    assertNotNull("Could not find module with name 'root.middle.middle-sources.main'", middleSharedMain)
    middleSharedTest = modules.find(_.getName == "root.middle.middle-sources.test").orNull
    assertNotNull("Could not find module with name 'root.middle.middle-sources.test'", middleSharedTest)
    baseJSMain = modules.find(_.getName == "root.base.baseJS.main").orNull
    assertNotNull("Could not find module with name 'root.base.baseJS.main'", baseJSMain)
    baseJSTest = modules.find(_.getName == "root.base.baseJS.test").orNull
    assertNotNull("Could not find module with name 'root.base.baseJS.test'", baseJSTest)
    baseJVMMain = modules.find(_.getName == "root.base.baseJVM.main").orNull
    assertNotNull("Could not find module with name 'root.base.baseJVM.main'", baseJVMMain)
    baseJVMTest = modules.find(_.getName == "root.base.baseJVM.test").orNull
    assertNotNull("Could not find module with name 'root.base.baseJVM.test'", baseJVMTest)
    baseSharedMain = modules.find(_.getName == "root.base.base-sources.main").orNull
    assertNotNull("Could not find module with name 'root.base.base-sources.main'", baseSharedMain)
    baseSharedTest = modules.find(_.getName == "root.base.base-sources.test").orNull
    assertNotNull("Could not find module with name 'root.base.base-sources.test'", baseSharedTest)
    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)
  }

  def testSharedSourcesOnlyCompiledToOwnerModules(): Unit = {
    val messages1 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages1)

    Seq(middleJSMain, middleJVMMain).foreach { module =>
      val sharedClass = findClassFile("Shared", module, isTest = false)
      assertNotNull(s"Shared class file not found in ${module.getName}", sharedClass)
    }

    def fileIsNullAssertion(sharedClass: File, moduleName: String): Unit =
      assertNull(s"Shared class file found in $moduleName, but it shouldn't", sharedClass)

    Seq(baseJSMain,baseJVMMain).foreach { module =>
      val sharedClass = findClassFile("Shared", module, isTest = false)
      fileIsNullAssertion(sharedClass, module.getName)
    }

    Seq(baseJSTest, middleJSTest, middleJVMTest, baseJVMTest).foreach { module =>
      val sharedClass = findClassFile("Shared", module, isTest = true)
      fileIsNullAssertion(sharedClass, module.getName)
    }
  }
}
