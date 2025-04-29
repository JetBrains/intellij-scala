package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.CompilerTester
import org.jetbrains.jps.util.JpsPathUtil
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._
import scala.util.Using

@Category(Array(classOf[SlowTests]))
@RunWith(classOf[JUnit4])
class ClashingOutputDirectoriesTest extends SbtProjectCompilationTestBase(separateProdAndTestSources = true) {

  @Test
  def outputDirectoriesClash(): Unit = {
    createProjectSubDirs("project", "module1/src/main/scala", "module2/src/main/scala")
    createProjectSubFile("project/build.properties", "sbt.version=1.10.11")
    createProjectConfig(
      """ThisBuild / scalaVersion := "3.6.4"
        |lazy val root = project.in(file(".")).aggregate(module1, module2)
        |lazy val module1 = project.in(file("module1"))
        |lazy val module2 = project.in(file("module2"))
        |""".stripMargin)
    createProjectSubFile("module1/src/main/scala/One.scala", "class One")
    createProjectSubFile("module2/src/main/scala/Two.scala", "class Two")
    importProject(false)

    val allModules = ModuleManager.getInstance(getProject).getModules.toList
    val module1 = allModules.find(_.getName == "root.module1.main").orNull
    val module2 = allModules.find(_.getName == "root.module2.main").orNull

    implicit val pathReleasable: Using.Releasable[Path] = { dir =>
      def deleteRecursively(path: Path): Unit = {
        if (path.isDirectory)
          path.children().foreach(deleteRecursively)
        Files.deleteIfExists(dir)
      }

      deleteRecursively(dir)
    }

    Using.resource(Files.createTempDirectory("clashing-output-directories-test-")) { dir =>
      val pathUrl = VfsUtilCore.pathToUrl(dir.toString)
      setOutputPath(module1, pathUrl)
      setOutputPath(module2, pathUrl)

      compiler = new CompilerTester(getProject, allModules.asJava, null, false)
      val Seq(errorMessage) = compiler.make().asScala.toSeq

      val jpsUrl = JpsPathUtil.urlToNioPath(pathUrl)
      val expected =
        s"""scala: Output path $jpsUrl is shared between: ${Seq(module1, module2).map(m => s"Module '${m.getName}' production").mkString(", ")}
           |Please configure separate output paths to proceed with the compilation.
           |TIP: you can use Project Artifacts to combine compiled classes if needed.""".stripMargin
      assertEquals(expected, errorMessage.getMessage)
    }
  }

  private def setOutputPath(module: Module, pathUrl: String): Unit = inWriteAction {
    val model = module.modifiableModel
    val extension = model.getModuleExtension(classOf[CompilerModuleExtension])
    extension.setCompilerOutputPath(pathUrl)
    model.commit()
  }
}
