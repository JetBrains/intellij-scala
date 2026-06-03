package org.jetbrains.plugins.scala.compiler

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.server.CompileServerToken
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.jdk.CollectionConverters.CollectionHasAsScala

@RunWith(classOf[JUnit4])
class CompileServerTokenIntegrationTest extends ScalaCompilerTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def testProjectJdkVersion: LanguageLevel = LanguageLevel.JDK_21

  @Test
  def tokenCreated(): Unit = {
    addFileToProjectSources("org/example/Person.scala",
      """package org.example
        |
        |final case class Person(name: String, age: Int)
        |""".stripMargin)

    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)

    val compileServerSystemDir = CompileServerLauncher.scalaCompileServerSystemDir(getProject)
    val token = CompileServerLauncher.compileServerPort match {
      case Some(port) => CompileServerToken.tokenForPort(compileServerSystemDir, port.forToken)
      case None => throw new AssertionError("Cannot connect to Scala Compile Server: unknown TCP port, make sure the server is running")
    }
    assertTrue("Could not read the Scala Compile Server token for the test project", token.nonEmpty)
    assertTrue("The token string is empty", token.get.nonEmpty)

    // The exact value of the token is intentionally not asserted as we intentionally do not have a way of injecting
    // a specific token value for tests. If we did, this would open up a way to provide a token value and take control
    // of the Scala Compile Server for untrusted code execution.
  }
}
