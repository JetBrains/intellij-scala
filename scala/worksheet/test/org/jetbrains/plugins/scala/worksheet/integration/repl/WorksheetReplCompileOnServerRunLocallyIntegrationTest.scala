package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}

import scala.language.postfixOps

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
class WorksheetReplCompileOnServerRunLocallyIntegrationTest extends WorksheetReplIntegrationBaseTest {

  override def useCompileServer: Boolean = true

  override def runInCompileServerProcess: Boolean = false

  // if compile server is enabled we still use it regarding of what is the value of runInCompileServerProcess setting
  def testSimpleDeclaration(): Unit =
    doRenderTest("42", "val res0: Int = 42")
}
