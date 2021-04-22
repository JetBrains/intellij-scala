package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.jetbrains.plugins.scala.worksheet.integration.{WorksheetIntegrationBaseTest, WorksheetRunTestSettings}
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType
import org.junit.Ignore

trait WorksheetPlainCheckRuntimeVersionScalaTests  {
  self: WorksheetIntegrationBaseTest =>

  override protected def supportedIn(version: ScalaVersion): Boolean = true

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_2_11_0,
    TestScalaVersion.Scala_2_11,
    TestScalaVersion.Scala_2_12_0,
    TestScalaVersion.Scala_2_12
  ))
  def testRuntimeScalaVersion_BeforeScala_2_13(): Unit = {
    val scalaVersion = this.version
    doRenderTest(
      s"util.Properties.versionString",
      s"res0: String = version ${scalaVersion.minor}"
    )
  }

  @RunWithScalaVersions(Array(
    // don't run for 2.13.0, cause it has an error which requires JLine to be present in classpath (see SCL-15818, SCL-15948)
    //TestScalaVersion.Scala_2_13_0,
    TestScalaVersion.Scala_2_13
  ))
  def testRuntimeScalaVersion_Scala_2_13(): Unit = {
    val scalaVersion = this.version
    doRenderTest(
      s"util.Properties.versionString",
      s"res0: String = version ${scalaVersion.minor}"
    )
  }

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_3_0,
  ))
  def testRuntimeScalaVersion_Scala_3(): Unit = {
    val runtimeScalaVersion = "2.13.5" // in Scala3 a version from 2.13 scala-library.jar is used
    doRenderTest(
      s"util.Properties.versionString",
      s"val res0: String = version $runtimeScalaVersion"
    )
  }
}