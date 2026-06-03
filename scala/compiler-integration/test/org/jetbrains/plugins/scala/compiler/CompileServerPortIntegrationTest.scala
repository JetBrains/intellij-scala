package org.jetbrains.plugins.scala.compiler

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.server.CompileServerPort
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.jdk.CollectionConverters.CollectionHasAsScala

@RunWith(classOf[JUnit4])
class CompileServerPortIntegrationTest extends ScalaCompilerTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def testProjectJdkVersion: LanguageLevel = LanguageLevel.JDK_21

  @Test
  def portFileCreated(): Unit = {
    addFileToProjectSources("org/example/Person.scala",
      """package org.example
        |
        |final case class Person(name: String, age: Int)
        |""".stripMargin)

    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)

    val compileServerSystemDir = CompileServerLauncher.scalaCompileServerSystemDir(getProject)
    val expected = CompileServerLauncher.compileServerPort
    assertTrue(s"Compile server is not running", CompileServerLauncher.running)
    assertTrue(s"Compile server port is not defined", expected.isDefined)
    val actual = CompileServerPort.readPortFile(compileServerSystemDir).map(CompileServerPort.Local.apply)
    assertEquals(expected, actual)
  }
}
