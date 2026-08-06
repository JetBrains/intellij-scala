package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.{assertCompilingScalaSources, assertNoErrorsOrWarnings}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.jetbrains.plugins.scala.{CompilationTests_IDEA, CompilationTests_Zinc}
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import scala.jdk.CollectionConverters._

@RunWith(classOf[Parameterized])
class ClasspathOrderingCompilationTest(jdkVersion: TestJdkVersion) extends SbtProjectCompilationTestBase {

  override protected def jdkVersionForTest: TestJdkVersion = jdkVersion

  @Test
  @Category(Array(classOf[CompilationTests_Zinc]))
  def classpathOrdering_Zinc(): Unit = {
    runClasspathOrderingTest(IncrementalityType.SBT)
  }

  @Test
  @Category(Array(classOf[CompilationTests_IDEA]))
  def classpathOrdering_IDEA(): Unit = {
    runClasspathOrderingTest(IncrementalityType.IDEA)
  }

  private def runClasspathOrderingTest(incrementality: IncrementalityType): Unit = {
    createProjectSubDirs("project", "src/main/scala")
    createProjectSubFile("project/build.properties", "sbt.version=1.10.1")
    createProjectSubFile("src/main/scala/Test.scala",
      """case class Test(value: String)
        |
        |object Test {
        |
        |  extension (test: Test) {
        |    def upper: Test = Test(test.value.toUpperCase)
        |  }
        |}
        |""".stripMargin)
    createProjectConfig(
      s"""lazy val root = project.in(file("."))
         |  .settings(
         |    scalaVersion := "3.4.2",
         |    libraryDependencies += "com.gu" %% "play-v30-brotli-filter" % "0.16.1"
         |  )
         |""".stripMargin
    )

    importProject(false)
    ScalaCompilerConfiguration.instanceIn(getMyProject).incrementalityType = incrementality

    val modules = ModuleManager.getInstance(getMyProject).getModules
    rootModule = modules.find(_.getName == "root").orNull
    assertNotNull("Could not find module with name 'root'", rootModule)
    compiler = new CompilerTester(getMyProject, java.util.Arrays.asList(modules*), null, false)

    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)

    incrementality match {
      case IncrementalityType.SBT => assertCompilingScalaSources(messages, 1)
      case IncrementalityType.IDEA =>
    }

    val testClass = findClassFile(rootModule, "Test")
    assertNotNull("Could not find compiled Test.class", testClass)
    val testObject = findClassFile(rootModule, "Test$")
    assertNotNull("Could not find compiled Test$.class", testObject)
  }
}

private object ClasspathOrderingCompilationTest extends JdkVersionParameters
