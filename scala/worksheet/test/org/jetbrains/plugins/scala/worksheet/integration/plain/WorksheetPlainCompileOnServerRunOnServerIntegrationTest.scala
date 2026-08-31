package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.jetbrains.plugins.scala.util.runners.{RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}

class WorksheetPlainCompileOnServerRunOnServerIntegrationTest
  extends CompileOnServerRunOnServerTestBase
    with WorksheetPlainIntegration_CommonTests

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11_0,
  TestScalaVersion.Scala_2_12_0,
  TestScalaVersion.Scala_2_13_0
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_11))
class WorksheetPlainCompileOnServerRunOnServerIntegrationTest_HealthCheck_OldScalaVersions
  extends CompileOnServerRunOnServerTestBase
    with WorksheetPlainIntegration_HealthCheckTest

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12))
class WorksheetPlainCompileOnServerRunOnServerIntegrationTest_Scala_2_12_SpecificTests
  extends CompileOnServerRunOnServerTestBase
    with WorksheetPlainIntegration_Scala_2_12_SpecificTests

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_0,
  TestScalaVersion.Scala_3_1,
  TestScalaVersion.Scala_3_2
))
class WorksheetPlainCompileOnServerRunOnServerIntegrationTest_Scala_3_AllInOne
  extends CompileOnServerRunOnServerTestBase
    with WorksheetPlainIntegrationTestBase_Scala_3_AllInOne

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_4,
  TestScalaVersion.Scala_3_5,
  TestScalaVersion.Scala_3_6,
  TestScalaVersion.Scala_3_7
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_17))
class WorksheetPlainCompileOnServerRunOnServerIntegrationTest_Scala_3_4_to_3_7_AllInOne
  extends CompileOnServerRunOnServerTestBase
    with WorksheetPlainIntegrationTestBase_Scala_3_4_to_3_7_AllInOne

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_3,
  TestScalaVersion.Scala_3_Latest_RC
))
class WorksheetPlainCompileOnServerRunOnServerIntegrationTest_Scala_3_LTS_AllInOne
  extends CompileOnServerRunOnServerTestBase
    with WorksheetPlainIntegrationTestBase_Scala_3_RC_AllInOne

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_9,
  TestScalaVersion.Scala_3_Next_RC
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_17))
class WorksheetPlainCompileOnServerRunOnServerIntegrationTest_Scala_3_Next_RC_AllInOne
  extends CompileOnServerRunOnServerTestBase
    with WorksheetPlainIntegrationTestBase_Scala_3_RC_AllInOne

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_0,
  TestScalaVersion.Scala_3_1,
  TestScalaVersion.Scala_3_2,
  TestScalaVersion.Scala_3_3,
  TestScalaVersion.Scala_3_Latest_RC
))
class WorksheetPlainCompileOnServerRunOnServerIntegrationTest_Scala_3_BracelessSyntax
  extends CompileOnServerRunOnServerTestBase
    with WorksheetPlainIntegrationTestBase_Scala_3_BracelessSyntax

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_9,
  TestScalaVersion.Scala_3_Next_RC
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_17))
class WorksheetPlainCompileOnServerRunOnServerIntegrationTest_Scala_3_BracelessSyntax_Next_RC
  extends WorksheetPlainCompileOnServerRunOnServerIntegrationTest_Scala_3_BracelessSyntax
