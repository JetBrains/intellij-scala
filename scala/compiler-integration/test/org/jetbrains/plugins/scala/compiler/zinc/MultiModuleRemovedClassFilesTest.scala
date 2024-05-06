package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.Assert.assertNotNull
import org.junit.experimental.categories.Category

import java.nio.file.Path
import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
class MultiModuleRemovedClassFilesTest extends ZincTestBase {

  private var module1: Module = _

  private var module2: Module = _

  override def setUp(): Unit = {
    super.setUp()

    createProjectSubDirs("project", "module1/src/main/scala", "module2/src/main/scala")
    createProjectSubFile("project/build.properties", "sbt.version=1.9.7")
    createProjectSubFile("module1/src/main/scala/Foo.scala", "class Foo")
    createProjectSubFile("module2/src/main/scala/Bar.scala", "class Bar extends Foo")
    createProjectConfig(
      """lazy val root = project.in(file("."))
        |  .aggregate(module1, module2)
        |
        |lazy val module1 = project.in(file("module1"))
        |  .settings(scalaVersion := "2.13.12")
        |
        |lazy val module2 = project.in(file("module2"))
        |  .dependsOn(module1)
        |  .settings(scalaVersion := "2.13.12")
        |""".stripMargin)

    importProject(false)
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = IncrementalityType.SBT

    val modules = ModuleManager.getInstance(myProject).getModules
    module1 = modules.find(_.getName == "root.module1").orNull
    assertNotNull("Could not find module with name 'root.module1'", module1)
    module2 = modules.find(_.getName == "root.module2").orNull
    assertNotNull("Could not find module with name 'root.module2'", module2)
    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)
  }

  def testRemoveDependencyClassFile(): Unit = {
    val messages1 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages1)

    val fooClass = findClassFile(module1, "Foo")
    removeFile(fooClass)

    val projectPath = Path.of(getProjectPath)
    val srcMainScala = Path.of("src", "main", "scala")

    val barSourcePath = projectPath.resolve(Path.of("module2").resolve(srcMainScala).resolve("Bar.scala"))
    val barSource = VfsUtil.findFileByIoFile(barSourcePath.toFile, true)
    inWriteAction {
      VfsUtil.saveText(barSource, "class Bar extends Foo { def foo = 1 }")
    }

    val messages2 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages2)
  }

}
