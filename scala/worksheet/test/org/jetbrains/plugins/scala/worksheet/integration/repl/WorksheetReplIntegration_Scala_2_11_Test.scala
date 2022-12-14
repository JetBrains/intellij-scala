package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.util.runners.{RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetRuntimeExceptionsTests

import scala.language.postfixOps

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_11))
class WorksheetReplIntegration_Scala_2_11_Test
  extends WorksheetReplIntegrationBaseTest
    with WorksheetRuntimeExceptionsTests
    with WorksheetReplIntegration_CommonTests_Since_2_11 {

  // Some health check runs
  @RunWithScalaVersions(Array(TestScalaVersion.Scala_2_11_0))
  @RunWithJdkVersions(Array(TestJdkVersion.JDK_11))
  def testSimpleDeclaration__2_11_0(): Unit =
    testSimpleDeclaration()
}
