package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
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
class CompileToTestDependencyTest(jdkVersion: TestJdkVersion)
  extends SbtProjectCompilationTestBase(separateProdAndTestSources = true) {

  override protected def jdkVersionForTest: TestJdkVersion = jdkVersion

  @Test
  @Category(Array(classOf[CompilationTests_Zinc]))
  def compileToTestDependency_Zinc(): Unit = {
    runCompileToTestDependencyTest(IncrementalityType.SBT)
  }

  @Test
  @Category(Array(classOf[CompilationTests_IDEA]))
  def compileToTestDependency_IDEA(): Unit = {
    runCompileToTestDependencyTest(IncrementalityType.IDEA)
  }

  private def runCompileToTestDependencyTest(incrementality: IncrementalityType): Unit = {
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
    ScalaCompilerConfiguration.instanceIn(getMyProject).incrementalityType = incrementality

    val modules = ModuleManager.getInstance(getMyProject).getModules

    Seq("root.main", "root.test").foreach { moduleName =>
      val module = modules.find(_.getName == moduleName).orNull
      assertNotNull(s"Could not find module with name $moduleName", module)
    }

    compiler = new CompilerTester(getMyProject, java.util.Arrays.asList(modules: _*), null, false)
    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)
  }
}

private object CompileToTestDependencyTest extends JdkVersionParameters
