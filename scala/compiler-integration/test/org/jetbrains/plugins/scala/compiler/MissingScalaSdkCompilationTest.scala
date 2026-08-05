package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.roots.{LibraryOrderEntry, ModuleRootManager}
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.LibraryExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.jetbrains.plugins.scala.{CompilationTests_IDEA, CompilationTests_Zinc}
import org.junit.Assert.{assertEquals, assertNotNull}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.CollectionHasAsScala

@RunWith(classOf[Parameterized])
class MissingScalaSdkCompilationTest(jdkVersion: TestJdkVersion)
  extends SbtProjectCompilationTestBase(separateProdAndTestSources = true) {

  override protected def jdkVersionForTest: TestJdkVersion = jdkVersion

  private var module1: Module = uninitialized
  private var module2: Module = uninitialized
  private var module3: Module = uninitialized

  @Test
  @Category(Array(classOf[CompilationTests_Zinc]))
  def missingScalaSdkWarning_Zinc(): Unit = {
    runMissingScalaSdkWarningTest(IncrementalityType.SBT)
  }

  @Test
  @Category(Array(classOf[CompilationTests_IDEA]))
  def missingScalaSdkWarning_IDEA(): Unit = {
    runMissingScalaSdkWarningTest(IncrementalityType.IDEA)
  }

  private def runMissingScalaSdkWarningTest(incrementality: IncrementalityType): Unit = {
    createProjectSubDirs("project", "module1/src/main/scala", "module2/src/main/scala", "module3/src/main/scala")
    createProjectSubFile("project/build.properties", "sbt.version=1.11.2")
    createProjectSubFile("module1/src/main/scala/One.scala", "class One")
    createProjectSubFile("module2/src/main/scala/Two.scala", "class Two")
    createProjectSubFile("module3/src/main/scala/Three.scala", "class Three")
    createProjectConfig(
      """ThisBuild / scalaVersion := "3.7.1"
        |
        |lazy val root = project.in(file("."))
        |  .aggregate(module1, module2, module3)
        |  .settings(
        |    name := "missingScalaSdkTest"
        |  )
        |
        |lazy val module1 = project.in(file("module1"))
        |lazy val module2 = project.in(file("module2"))
        |lazy val module3 = project.in(file("module3"))
        |""".stripMargin)
    importProject(false)

    ScalaCompilerConfiguration.instanceIn(getMyProject).incrementalityType = incrementality

    val modules = ModuleManager.getInstance(getMyProject).getModules
    rootModule = findModule("missingScalaSdkTest.main", modules)
    module1 = findModule("missingScalaSdkTest.module1.main", modules)
    module2 = findModule("missingScalaSdkTest.module2.main", modules)
    module3 = findModule("missingScalaSdkTest.module3.main", modules)
    compiler = new CompilerTester(getMyProject, java.util.Arrays.asList(modules*), null, false)

    removeScalaSdk(module2)
    removeScalaSdk(module3)

    val messages = compiler.make().asScala.toSeq
    val warnings = messages.collect {
      case message if message.getCategory == CompilerMessageCategory.WARNING => message
    }
    assertEquals(2, warnings.size)
    val Seq(warning2, warning3) = warnings.map(_.getMessage).sorted
    assertEquals(missingScalaSdkWarningMessage(module2), warning2)
    assertEquals(missingScalaSdkWarningMessage(module3), warning3)
  }

  private def findModule(name: String, modules: Array[Module]): Module = {
    val m = modules.find(_.getName == name).orNull
    assertNotNull(s"Could not find module with name $name", m)
    m
  }

  private def removeScalaSdk(module: Module): Unit = inWriteAction {
    val model = ModuleRootManager.getInstance(module).getModifiableModel
    val entries = model.getOrderEntries
    entries.foreach {
      case entry: LibraryOrderEntry if entry.getLibrary.isScalaSdk =>
        model.removeOrderEntry(entry)
      case _ => // skip other entries
    }
    model.commit()
  }

  private def missingScalaSdkWarningMessage(module: Module): String =
    s"${MissingScalaSdk.MessagePrefix}: ${MissingScalaSdk.skippedModuleMessage(module.getName)}"
}

private object MissingScalaSdkCompilationTest extends JdkVersionParameters
