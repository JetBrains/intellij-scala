package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.Assert.assertNotNull
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
class CompileToTestDependencyTest extends ZincTestBase(separateProdAndTestSources = true) {

  def testCompileToTestDependency_Zinc(): Unit =
    runTest(IncrementalityType.SBT)

  def testCompileToTestDependency_IDE(): Unit =
    runTest(IncrementalityType.IDEA)

  private def runTest(incrementality: IncrementalityType): Unit  = {
    createProjectSubDirs(
      "project",
      "src/main/scala",
      "src/test/scala",
      "foo/src/test/scala",
    )
    createProjectSubFile("project/build.properties", "sbt.version=1.9.7")
    createProjectSubFile("foo/src/test/scala/FooTest.scala", "class FooTest")
    createProjectSubFile("src/test/scala/RootTest.scala", "object RootTest { val dummy = new FooTest }")
    createProjectSubFile("src/main/scala/RootMain.scala", "object RootMain { val dummy = new FooTest }")
    createProjectConfig(
      """lazy val foo = project.in(file("foo"))
        |
        |lazy val root = project.in(file("."))
        |  .dependsOn(foo % "compile->test")
        |""".stripMargin)

    importProject(false)
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = incrementality

    val modules = ModuleManager.getInstance(myProject).getModules

    Seq("root.main", "root.test").foreach { moduleName =>
      val module = modules.find(_.getName == moduleName).orNull
      assertNotNull(s"Could not find module with name $moduleName", module)
    }

    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)
    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)
  }
}
