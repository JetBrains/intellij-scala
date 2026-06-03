package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.util.runners.{RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.junit.Test

@RunWithJdkVersions(Array(TestJdkVersion.JDK_1_8, TestJdkVersion.JDK_11))
abstract class WorksheetReplIntegrationSystemExitTestBase extends WorksheetReplIntegrationBaseTest

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_11, TestScalaVersion.Scala_2_12))
class WorksheetReplIntegrationSystemExitTest_Before_Scala_2_13 extends WorksheetReplIntegrationSystemExitTestBase {
  @Test
  def systemExit(): Unit = doRenderTest(
    """val x = 42
      |println(s"x: $x")
      |System.exit(0)""".stripMargin,
    """x: Int = 42
      |x: 42""".stripMargin
  )
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
class WorksheetReplIntegrationSystemExitTest_2_13 extends WorksheetReplIntegrationSystemExitTestBase {
  @Test
  def systemExit(): Unit = doRenderTest(
    """val x = 42
      |println(s"x: $x")
      |System.exit(0)""".stripMargin,
    """val x: Int = 42
      |x: 42""".stripMargin
  )
}
