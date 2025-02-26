package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.CompilerTester
import junit.framework.TestCase.{assertEquals, assertNull}
import org.hamcrest.CoreMatchers.{containsString, not}
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
class MacroCompilationErrorSourcePathTest extends ZincTestBase {

  def testMacroCompilationErrorSourcePath_Zinc(): Unit = {
    runMacroCompilationErrorSourcePathTest(IncrementalityType.SBT)
  }

  def testMacroCompilationErrorSourcePath_IDEA(): Unit = {
    runMacroCompilationErrorSourcePathTest(IncrementalityType.IDEA)
  }

  private def runMacroCompilationErrorSourcePathTest(incrementalityType: IncrementalityType): Unit = {
    setUpSbtProject(incrementalityType)

    val messages = compiler.make().asScala.toSeq
    val errors = messages.filter(_.getCategory == CompilerMessageCategory.ERROR)
    val errorsCount = errors.size
    assertEquals(s"Expected 1 error message, got: $errorsCount", 1, errorsCount)

    val error = errors.head
    assertNull("The macro compilation error should not point to any particular source", error.getVirtualFile)

    val message = error.getMessage
    assertThat(message, containsString("not found: value extremelySpecificCompilationError"))
    assertThat(message, not(containsString("java.nio.file.InvalidPathException")))
    assertThat(message, not(containsString("<macro>")))
  }

  private def setUpSbtProject(incrementalityType: IncrementalityType): Unit = {
    createProjectSubDirs("project", "src/main/scala", "macros/src/main/scala")
    createProjectSubFile("project/build.properties", "sbt.version=1.10.7")
    createProjectSubFile("macros/src/main/scala/Macros.scala",
      """import scala.language.experimental.macros
        |import scala.reflect.macros.blackbox
        |
        |object Macros {
        |  def macroImpl(c: blackbox.Context)(s: c.Expr[String]): c.Expr[String] = {
        |    c.Expr(c.parse(s" extremelySpecificCompilationError "))
        |  }
        |
        |  def macroTest(s: String): String = macro macroImpl
        |}
        |""".stripMargin)
    createProjectSubFile("src/main/scala/Main.scala",
      """object Main {
        |  def main(args: Array[String]): Unit = {
        |    println(Macros.macroTest("hello"))
        |  }
        |}
        |""".stripMargin)
    createProjectConfig(
      """ThisBuild / scalaVersion := "2.13.16"
        |
        |lazy val root = project.in(file("."))
        |  .dependsOn(macros)
        |  .settings(
        |    name := "scala-macros-repro"
        |  )
        |
        |lazy val macros = project.in(file("macros"))
        |  .settings(
        |    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
        |  )
        |""".stripMargin)

    importProject(false)
    ScalaCompilerConfiguration.instanceIn(getProject).incrementalityType = incrementalityType

    val modules = ModuleManager.getInstance(getProject).getModules
    compiler = new CompilerTester(getProject, java.util.Arrays.asList(modules: _*), null, false)
  }
}
