package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.{assertCompilingScalaSources, assertNoErrorsOrWarnings}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.{CompilationTests_IDEA, CompilationTests_Zinc}
import org.junit.Assert.assertNotNull
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

abstract class ClasspathOrderingCompilationTestBase(incrementality: IncrementalityType) extends SbtProjectCompilationTestBase {

  def testClasspathOrdering(): Unit = {
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
    compiler = new CompilerTester(getMyProject, java.util.Arrays.asList(modules: _*), null, false)

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

@Category(Array(classOf[CompilationTests_Zinc]))
class ClasspathOrderingCompilationTest_Zinc extends ClasspathOrderingCompilationTestBase(IncrementalityType.SBT)

@Category(Array(classOf[CompilationTests_IDEA]))
class ClasspathOrderingCompilationTest_IDEA extends ClasspathOrderingCompilationTestBase(IncrementalityType.IDEA)
