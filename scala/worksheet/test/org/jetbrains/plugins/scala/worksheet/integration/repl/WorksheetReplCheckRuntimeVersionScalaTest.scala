package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.util.runners.{RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}

class WorksheetReplCheckRuntimeVersionScalaTest extends WorksheetReplIntegrationBaseTest {

  override protected def supportedIn(version: ScalaVersion): Boolean = true

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_2_11,
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
    TestScalaVersion.Scala_2_11_0,
    TestScalaVersion.Scala_2_12_0
  ))
  @RunWithJdkVersions(Array(TestJdkVersion.JDK_11))
  def testRuntimeScalaVersion_BeforeScala_2_13_OldScalaVersions(): Unit =
    testRuntimeScalaVersion_BeforeScala_2_13()

  @RunWithScalaVersions(Array(
    // don't run for 2.13.0, cause it has an error which requires JLine to be present in classpath (see SCL-15818, SCL-15948)
    //TestScalaVersion.Scala_2_13_0,
    TestScalaVersion.Scala_2_13
  ))
  def testRuntimeScalaVersion_Scala_2_13(): Unit = {
    val scalaVersion = this.version
    doRenderTest(
      s"util.Properties.versionString",
      s"val res0: String = version ${scalaVersion.minor}"
    )
  }

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_3_0
  ))
  def testRuntimeScalaVersion_Scala_3_0(): Unit = {
    val runtimeScalaVersion = "2.13.6" // in Scala3 a version from 2.13 scala-library.jar is used
    doRenderTest(
      s"util.Properties.versionString",
      s"val res0: String = version $runtimeScalaVersion"
    )
  }

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_3_1
  ))
  def testRuntimeScalaVersion_Scala_3_1(): Unit = {
    val runtimeScalaVersion = "2.13.8" // in Scala3 a version from 2.13 scala-library.jar is used
    doRenderTest(
      s"util.Properties.versionString",
      s"val res0: String = version $runtimeScalaVersion"
    )
  }

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_3_2
  ))
  def testRuntimeScalaVersion_Scala_3_2(): Unit = {
    val runtimeScalaVersion = "2.13.10" // in Scala3 a version from 2.13 scala-library.jar is used
    doRenderTest(
      s"util.Properties.versionString",
      s"val res0: String = version $runtimeScalaVersion"
    )
  }

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_3_3
  ))
  def testRuntimeScalaVersion_Scala_3_3(): Unit = {
    val runtimeScalaVersion = "2.13.12" // in Scala3 a version from 2.13 scala-library.jar is used
    doRenderTest(
      s"util.Properties.versionString",
      s"val res0: String = version $runtimeScalaVersion"
    )
  }
}
