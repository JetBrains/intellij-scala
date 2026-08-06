package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.util.runners.{RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.junit.Test

abstract class WorksheetReplCheckRuntimeVersionScalaTestBase extends WorksheetReplIntegrationBaseTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12
))
class WorksheetReplCheckRuntimeVersionScalaTest_BeforeScala_2_13 extends WorksheetReplCheckRuntimeVersionScalaTestBase {
  @Test
  def testRuntimeScalaVersion_BeforeScala_2_13(): Unit = {
    val scalaVersion = this.version
    doRenderTest(
      s"util.Properties.versionString",
      s"res0: String = version ${scalaVersion.minor}"
    )
  }
}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11_0,
  TestScalaVersion.Scala_2_12_0
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_11))
class WorksheetReplCheckRuntimeVersionScalaTest_OldScalaVersions extends WorksheetReplCheckRuntimeVersionScalaTest_BeforeScala_2_13

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
class WorksheetReplCheckRuntimeVersionScalaTest_2_13 extends WorksheetReplCheckRuntimeVersionScalaTestBase {

  protected def expectedScalaRuntimeVersion: String = this.version.minor

  @Test
  def testRuntimeScalaVersion_Scala_2_13(): Unit = {
    doRenderTest(
      s"util.Properties.versionString",
      s"val res0: String = version $expectedScalaRuntimeVersion"
    )
  }
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_0))
class WorksheetReplCheckRuntimeVersionScalaTest_3_0 extends WorksheetReplCheckRuntimeVersionScalaTest_2_13 {
  override protected def expectedScalaRuntimeVersion: String = "2.13.6"
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_1))
class WorksheetReplCheckRuntimeVersionScalaTest_3_1 extends WorksheetReplCheckRuntimeVersionScalaTest_2_13 {
  override protected def expectedScalaRuntimeVersion: String = "2.13.8"
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_2))
class WorksheetReplCheckRuntimeVersionScalaTest_3_2 extends WorksheetReplCheckRuntimeVersionScalaTest_2_13 {
  override protected def expectedScalaRuntimeVersion: String = "2.13.10"
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_3))
class WorksheetReplCheckRuntimeVersionScalaTest_3_3 extends WorksheetReplCheckRuntimeVersionScalaTest_2_13 {
  override protected def expectedScalaRuntimeVersion: String = "2.13.18"
}
