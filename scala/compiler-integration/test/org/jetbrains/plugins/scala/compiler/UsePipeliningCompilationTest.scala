package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, TestJdkVersion, TestScalaVersion}
import org.jetbrains.plugins.scala.{CompilationTests_IDEA, CompilationTests_Zinc}
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.{JUnit4, Parameterized}

import scala.jdk.CollectionConverters._

@RunWith(classOf[Parameterized])
class UsePipeliningCompilationTest(scalaVersion: TestScalaVersion, jdkVersion: TestJdkVersion)
  extends SbtProjectCompilationTestBase {

  override protected def jdkVersionForTest: TestJdkVersion = jdkVersion

  private var module1: Module = _
  private var module2: Module = _
  private var module3: Module = _

  @Test
  @Category(Array(classOf[CompilationTests_Zinc]))
  def usePipelining_Zinc(): Unit = {
    runUsePipeliningTest(IncrementalityType.SBT)
  }

  @Test
  @Category(Array(classOf[CompilationTests_IDEA]))
  def usePipelining_IDEA(): Unit = {
    runUsePipeliningTest(IncrementalityType.IDEA)
  }

  private def runUsePipeliningTest(incrementality: IncrementalityType): Unit = {
    createProjectSubDirs("project", "module1/src/main/scala", "module2/src/main/scala", "module3/src/main/scala")
    createProjectSubFile("project/build.properties", "sbt.version=1.10.0")
    createProjectSubFile("module1/src/main/scala/Greeter.scala", "trait Greeter { def greeting: String }")
    createProjectSubFile("module2/src/main/scala/GoodMorningGreeter.scala",
      """object GoodMorningGreeter extends Greeter { override def greeting: String = "Good morning" }""")
    createProjectSubFile("module3/src/main/scala/GoodEveningGreeter.scala",
      """object GoodEveningGreeter extends Greeter { override def greeting: String = "Good evenging" }""")
    createProjectConfig(
      s"""ThisBuild / scalaVersion := "${scalaVersion.toProductionVersion.minor}"
         |ThisBuild / usePipelining := true
         |
         |lazy val root = project.in(file("."))
         |  .aggregate(module1, module2, module3)
         |lazy val module1 = project.in(file("module1"))
         |lazy val module2 = project.in(file("module2")).dependsOn(module1)
         |lazy val module3 = project.in(file("module3")).dependsOn(module1)
         |""".stripMargin
    )

    importProject(false)
    ScalaCompilerConfiguration.instanceIn(getMyProject).incrementalityType = incrementality

    val modules = ModuleManager.getInstance(getMyProject).getModules
    rootModule = modules.find(_.getName == "root").orNull
    assertNotNull("Could not find module with name 'root'", rootModule)
    module1 = modules.find(_.getName == "root.module1").orNull
    assertNotNull("Could not find module with name 'root.module1'", module1)
    module2 = modules.find(_.getName == "root.module2").orNull
    assertNotNull("Could not find module with name 'root.module2'", module2)
    module3 = modules.find(_.getName == "root.module3").orNull
    assertNotNull("Could not find module with name 'root.module3'", module3)
    compiler = new CompilerTester(getMyProject, java.util.Arrays.asList(modules: _*), null, false)

    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)

    val greetingClass = findClassFile(module1, "Greeter")
    assertNotNull(s"Could not find compiled class 'Greeter' in 'module1'", greetingClass)

    val goodMorningGreeterClass = findClassFile(module2, "GoodMorningGreeter")
    assertNotNull(s"Could not find compiled class 'GoodMorningGreeter' in 'module2'", goodMorningGreeterClass)
    val goodMorningGreeterObject = findClassFile(module2, "GoodMorningGreeter$")
    assertNotNull(s"Could not find compiled class 'GoodMorningGreeter$$' in 'module2'", goodMorningGreeterObject)

    val goodEveningGreeterClass = findClassFile(module3, "GoodEveningGreeter")
    assertNotNull(s"Could not find compiled class 'GoodEveningGreeter' in 'module3'", goodEveningGreeterClass)
    val goodEveningGreeterObject = findClassFile(module3, "GoodEveningGreeter$")
    assertNotNull(s"Could not find compiled class 'GoodEveningGreeter$$' in 'module3'", goodEveningGreeterObject)
  }
}

private object UsePipeliningCompilationTest {
  @Parameterized.Parameters(name = "{0}, {1}")
  def parameters: java.util.Collection[Array[AnyRef]] = {
    val scalaVersions = Seq(TestScalaVersion.Scala_2_12, TestScalaVersion.Scala_2_13)
    val registry = MultipleScalaVersionsRunner.filterJdkVersionRegistry
    val jdkFilter = (version: TestJdkVersion) => registry.forall(_ == version)
    val jdkVersions = TestJdkVersion.values().toSeq.filter(jdkFilter)

    val combinations = for {
      sv <- scalaVersions
      jv <- jdkVersions
    } yield Array[AnyRef](sv, jv)

    combinations.asJavaCollection
  }
}

@RunWith(classOf[JUnit4])
class UsePipeliningCompilationTest_Scala_Next_RC extends UsePipeliningCompilationTest(TestScalaVersion.Scala_3_Next_RC, TestJdkVersion.JDK_17)
