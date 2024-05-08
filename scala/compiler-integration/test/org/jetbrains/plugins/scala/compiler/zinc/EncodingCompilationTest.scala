package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.Assert.assertNotNull
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
class EncodingCompilationTest extends ZincTestBase {

  def testEncoding1_Zinc(): Unit = {
    runEncodingTest(Seq("-encoding", "UTF-8"), IncrementalityType.SBT)
  }

  def testEncoding1_IDEA(): Unit = {
    runEncodingTest(Seq("-encoding", "UTF-8"), IncrementalityType.IDEA)
  }

  def testEncoding2_Zinc(): Unit = {
    runEncodingTest(Seq("--encoding", "UTF-8"), IncrementalityType.SBT)
  }

  def testEncoding2_IDEA(): Unit = {
    runEncodingTest(Seq("--encoding", "UTF-8"), IncrementalityType.IDEA)
  }

  def testEncoding3_Zinc(): Unit = {
    runEncodingTest(Seq("-encoding:UTF-8"), IncrementalityType.SBT)
  }

  def testEncoding3_IDEA(): Unit = {
    runEncodingTest(Seq("-encoding:UTF-8"), IncrementalityType.IDEA)
  }

  def testEncoding4_Zinc(): Unit = {
    runEncodingTest(Seq("--encoding:UTF-8"), IncrementalityType.SBT)
  }

  def testEncoding4_IDEA(): Unit = {
    runEncodingTest(Seq("--encoding:UTF-8"), IncrementalityType.IDEA)
  }

  private def runEncodingTest(encodingSettings: Seq[String], incrementality: IncrementalityType): Unit = {
    createProjectSubDirs("project", "src/main/scala")
    createProjectSubFile("project/build.properties", "sbt.version=1.10.0")
    createProjectSubFile("src/main/scala/Foo.scala", "class Foo")
    createProjectConfig(
      s"""lazy val root = project.in(file("."))
         |  .settings(
         |    scalaVersion := "3.3.3",
         |    scalacOptions ++= Seq(${encodingSettings.map(s => s""""$s"""").mkString(", ")})
         |  )
         |""".stripMargin
    )

    importProject(false)
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = incrementality

    val modules = ModuleManager.getInstance(myProject).getModules
    rootModule = modules.find(_.getName == "root").orNull
    assertNotNull("Could not find module with name 'root'", rootModule)
    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)

    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)

    incrementality match {
      case IncrementalityType.SBT => assertCompilingScalaSources(messages, 1)
      case IncrementalityType.IDEA =>
    }
  }
}
