package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.jetbrains.plugins.scala.util.runners.{RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}

abstract class WorksheetPlainCompileOnServerRunOnServerCheckRuntimeVersionTestBase extends CompileOnServerRunOnServerTestBase

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13
))
class WorksheetPlainCompileOnServerRunOnServerCheckRuntimeVersionTest_Before_Scala_3
  extends WorksheetPlainCompileOnServerRunOnServerCheckRuntimeVersionTestBase
    with WorksheetPlainCheckRuntimeVersionScalaTests_Before_Scala_3

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11_0,
  TestScalaVersion.Scala_2_12_0
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_11))
class WorksheetPlainCompileOnServerRunOnServerCheckRuntimeVersionTest_Before_Scala_3_OldScalaVersions
  extends WorksheetPlainCompileOnServerRunOnServerCheckRuntimeVersionTestBase
    with WorksheetPlainCheckRuntimeVersionScalaTests_Before_Scala_3

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_0))
class WorksheetPlainCompileOnServerRunOnServerCheckRuntimeVersionTest_3_0
  extends WorksheetPlainCompileOnServerRunOnServerCheckRuntimeVersionTestBase
    with WorksheetPlainCheckRuntimeVersionScalaTests_Scala_3 {
  override protected def expectedRuntimeVersion: String = "2.13.6"
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_1))
class WorksheetPlainCompileOnServerRunOnServerCheckRuntimeVersionTest_3_1
  extends WorksheetPlainCompileOnServerRunOnServerCheckRuntimeVersionTestBase
    with WorksheetPlainCheckRuntimeVersionScalaTests_Scala_3 {
  override protected def expectedRuntimeVersion: String = "2.13.8"
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_2))
class WorksheetPlainCompileOnServerRunOnServerCheckRuntimeVersionTest_3_2
  extends WorksheetPlainCompileOnServerRunOnServerCheckRuntimeVersionTestBase
    with WorksheetPlainCheckRuntimeVersionScalaTests_Scala_3 {
  override protected def expectedRuntimeVersion: String = "2.13.10"
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_3))
class WorksheetPlainCompileOnServerRunOnServerCheckRuntimeVersionTest_3_3
  extends WorksheetPlainCompileOnServerRunOnServerCheckRuntimeVersionTestBase
    with WorksheetPlainCheckRuntimeVersionScalaTests_Scala_3 {
  override protected def expectedRuntimeVersion: String = "2.13.18"
}
