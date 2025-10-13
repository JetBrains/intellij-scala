package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.junit.Test

trait WorksheetPlainCheckRuntimeVersionScalaTests_Before_Scala_3 { self: PlainWorksheetTestBase =>
  @Test
  def testRuntimeScalaVersion(): Unit = {
    val scalaVersion = this.version
    doRenderTest(
      s"util.Properties.versionString",
      s"res0: String = version ${scalaVersion.minor}"
    )
  }
}

trait WorksheetPlainCheckRuntimeVersionScalaTests_Scala_3 { self: PlainWorksheetTestBase =>
  protected def expectedRuntimeVersion: String

  @Test
  def testRuntimeScalaVersion(): Unit = {
    doRenderTest(
      s"util.Properties.versionString",
      s"val res0: String = version $expectedRuntimeVersion"
    )
  }
}
