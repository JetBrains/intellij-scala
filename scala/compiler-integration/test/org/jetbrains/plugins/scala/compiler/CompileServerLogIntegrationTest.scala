package org.jetbrains.plugins.scala.compiler

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.server.CompileServerLog
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.jdk.CollectionConverters.CollectionHasAsScala

@RunWith(classOf[JUnit4])
class CompileServerLogIntegrationTest extends ScalaCompilerTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def testProjectJdkVersion: LanguageLevel = LanguageLevel.JDK_21

  @Test
  def logFileExists(): Unit = {
    addFileToProjectSources("org/example/Person.scala",
      """package org.example
        |
        |final case class Person(name: String, age: Int)
        |""".stripMargin)

    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)

    val logDir = CompileServerLauncher.logDirectory(getProject)
    val logFilePath = CompileServerLog.logFilePath(logDir)
    assertTrue("The Scala Compile Server log file was not created during compilation", logFilePath.exists)
  }
}
