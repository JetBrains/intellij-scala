package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.jetbrains.plugins.scala.util.runners.{RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}

class WorksheetPlainCompileOnServerRunLocallyIntegrationTest
  extends CompileOnServerRunLocallyTestBase
    with WorksheetPlainIntegration_CommonTests

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11_0,
  TestScalaVersion.Scala_2_12_0,
  TestScalaVersion.Scala_2_13_0
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_11))
class WorksheetPlainCompileOnServerRunLocallyIntegrationTest_HealthCheck_OldScalaVersions
  extends CompileOnServerRunLocallyTestBase
    with WorksheetPlainIntegration_HealthCheckTest

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12))
class WorksheetPlainCompileOnServerRunLocallyIntegrationTest_Scala_2_12_SpecificTests
  extends CompileOnServerRunLocallyTestBase
    with WorksheetPlainIntegration_Scala_2_12_SpecificTests

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_0,
  TestScalaVersion.Scala_3_1,
  TestScalaVersion.Scala_3_2
))
class WorksheetPlainCompileOnServerRunLocallyIntegrationTest_Scala_3_AllInOne
  extends CompileOnServerRunLocallyTestBase
    with WorksheetPlainIntegrationTestBase_Scala_3_AllInOne

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_4,
  TestScalaVersion.Scala_3_5,
  TestScalaVersion.Scala_3_6,
  TestScalaVersion.Scala_3_7
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_17))
class WorksheetPlainCompileOnServerRunLocallyIntegrationTest_Scala_3_4_to_3_7_AllInOne
  extends CompileOnServerRunOnServerTestBase
    with WorksheetPlainIntegrationTestBase_Scala_3_4_to_3_7_AllInOne

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_3,
  TestScalaVersion.Scala_3_Latest_RC
))
class WorksheetPlainCompileOnServerRunLocallyIntegrationTest_Scala_3_LTS_AllInOne
  extends CompileOnServerRunLocallyTestBase
    with WorksheetPlainIntegrationTestBase_Scala_3_RC_AllInOne

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_9,
  TestScalaVersion.Scala_3_Next_RC
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_17))
class WorksheetPlainCompileOnServerRunLocallyIntegrationTest_Scala_3_Next_RC_AllInOne
  extends CompileOnServerRunLocallyTestBase
    with WorksheetPlainIntegrationTestBase_Scala_3_RC_AllInOne

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_0,
  TestScalaVersion.Scala_3_1,
  TestScalaVersion.Scala_3_2,
  TestScalaVersion.Scala_3_3,
  TestScalaVersion.Scala_3_Latest_RC
))
class WorksheetPlainCompileOnServerRunLocallyIntegrationTest_Scala_3_BracelessSyntax
  extends CompileOnServerRunLocallyTestBase
    with WorksheetPlainIntegrationTestBase_Scala_3_BracelessSyntax

// TODO: This test is broken in the CI.
//@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Next_RC))
//@RunWithJdkVersions(Array(TestJdkVersion.JDK_17))
//class WorksheetPlainCompileOnServerRunLocallyIntegrationTest_Scala_3_BracelessSyntax_Next_RC
//  extends WorksheetPlainCompileOnServerRunLocallyIntegrationTest_Scala_3_BracelessSyntax
