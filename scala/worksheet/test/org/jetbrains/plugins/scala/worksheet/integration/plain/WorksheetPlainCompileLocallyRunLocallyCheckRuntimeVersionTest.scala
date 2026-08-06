package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.jetbrains.plugins.scala.util.runners.{RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}

abstract class WorksheetPlainCompileLocallyRunLocallyCheckRuntimeVersionTestBase extends CompileLocallyRunLocallyTestBase

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13
))
class WorksheetPlainCompileLocallyRunLocallyCheckRuntimeVersionTest_Before_Scala_3
  extends WorksheetPlainCompileLocallyRunLocallyCheckRuntimeVersionTestBase
    with WorksheetPlainCheckRuntimeVersionScalaTests_Before_Scala_3

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11_0,
  TestScalaVersion.Scala_2_12_0
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_11))
class WorksheetPlainCompileLocallyRunLocallyCheckRuntimeVersionTest_Before_Scala_3_OldScalaVersions
  extends WorksheetPlainCompileLocallyRunLocallyCheckRuntimeVersionTestBase
    with WorksheetPlainCheckRuntimeVersionScalaTests_Before_Scala_3

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_0))
class WorksheetPlainCompileLocallyRunLocallyCheckRuntimeVersionTest_3_0
  extends WorksheetPlainCompileLocallyRunLocallyCheckRuntimeVersionTestBase
    with WorksheetPlainCheckRuntimeVersionScalaTests_Scala_3 {
  override protected def expectedRuntimeVersion: String = "2.13.6"
}


@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_1))
class WorksheetPlainCompileLocallyRunLocallyCheckRuntimeVersionTest_3_1
  extends WorksheetPlainCompileLocallyRunLocallyCheckRuntimeVersionTestBase
    with WorksheetPlainCheckRuntimeVersionScalaTests_Scala_3 {
  override protected def expectedRuntimeVersion: String = "2.13.8"
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_2))
class WorksheetPlainCompileLocallyRunLocallyCheckRuntimeVersionTest_3_2
  extends WorksheetPlainCompileLocallyRunLocallyCheckRuntimeVersionTestBase
    with WorksheetPlainCheckRuntimeVersionScalaTests_Scala_3 {
  override protected def expectedRuntimeVersion: String = "2.13.10"
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_3))
class WorksheetPlainCompileLocallyRunLocallyCheckRuntimeVersionTest_3_3
  extends WorksheetPlainCompileLocallyRunLocallyCheckRuntimeVersionTestBase
    with WorksheetPlainCheckRuntimeVersionScalaTests_Scala_3 {
  override protected def expectedRuntimeVersion: String = "2.13.18"
}
