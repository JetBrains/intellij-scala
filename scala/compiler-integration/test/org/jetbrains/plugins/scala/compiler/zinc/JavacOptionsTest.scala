package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.Assert.{assertEquals, assertNotNull}
import org.junit.experimental.categories.Category

import java.net.URLClassLoader
import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
class JavacOptionsTest extends ZincTestBase {

  private var module1: Module = _

  private var module2: Module = _

  def testJavacOptions_Zinc(): Unit = {
    runJavacOptionsTest(IncrementalityType.SBT)
  }

  def testJavacOptions_IDEA(): Unit = {
    runJavacOptionsTest(IncrementalityType.IDEA)
  }

  private def runJavacOptionsTest(incrementality: IncrementalityType): Unit = {
    createProjectSubDirs("project", "module1/src/main/java", "module2/src/main/java")
    createProjectSubFile("project/build.properties", "sbt.version=1.10.0")
    createProjectSubFile("module1/src/main/java/org/example/Foo.java",
      """package org.example;
        |
        |public class Foo {
        |  public int getInt(boolean email) {
        |    return email ? 1 : 0;
        |  }
        |}
        |""".stripMargin)
    createProjectSubFile("module2/src/main/java/org/example/Bar.java",
      """package org.example;
        |
        |public class Bar {
        |  public int getInt(boolean email) {
        |    return email ? 1 : 0;
        |  }
        |}
        |""".stripMargin)
    createProjectConfig(
      s"""ThisBuild / scalaVersion := "3.3.3"
         |
         |lazy val root = project.in(file("."))
         |  .aggregate(module1, module2)
         |
         |lazy val javacParametersOptions = Seq(
         |  javacOptions += "-parameters"
         |)
         |
         |lazy val module1 = project.in(file("module1"))
         |  .settings(javacParametersOptions)
         |
         |lazy val module2 = project.in(file("module2"))
         |  .settings(javacParametersOptions)
         |  .settings(
         |    javacOptions ~= { _.filterNot(_ == "-parameters") }
         |  )
         |""".stripMargin
    )

    importProject(false)
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = incrementality

    val modules = ModuleManager.getInstance(myProject).getModules
    rootModule = modules.find(_.getName == "root").orNull
    assertNotNull("Could not find module with name 'root'", rootModule)
    module1 = modules.find(_.getName == "root.module1").orNull
    assertNotNull("Could not find module with name 'module1'", module1)
    module2 = modules.find(_.getName == "root.module2").orNull
    assertNotNull("Could not find module with name 'module2'", module2)
    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)

    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)

    val fooClassFile = findClassFile(module1, "org.example.Foo")
    assertNotNull("Could not find class file 'org.example.Foo' in 'module1'", fooClassFile)
    val barClassFile = findClassFile(module2, "org.example.Bar")
    assertNotNull("Could not find class file 'org.example.Bar' in 'module2'", barClassFile)

    val classLoader = new URLClassLoader(Array(fooClassFile, barClassFile).map(_.getParent.getParent.getParent.toUri.toURL))
    val fooClass = Class.forName("org.example.Foo", true, classLoader)
    val barClass = Class.forName("org.example.Bar", true, classLoader)

    val fooParameter = fooClass.getMethods.find(_.getName == "getInt").orNull.getParameters.head.getName
    assertEquals("Wrong compiled parameter name in class 'org.example.Foo'", "email", fooParameter)

    val barParameter = barClass.getMethods.find(_.getName == "getInt").orNull.getParameters.head.getName
    assertEquals("Wrong compiled parameter name in class 'org.example.Bar'", "arg0", barParameter)
  }
}
