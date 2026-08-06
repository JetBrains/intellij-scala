package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaVersion

import java.nio.file.Path

class ScStringLiteralAnnotatorTest_Scala2_Before_2_13_14 extends ScStringLiteralAnnotatorTestBase {
  override protected def relativeTestDataPath: Path = Path.of("annotator", "string_literals", "scala2_before_2_13_14")

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13.withMinor(13)
}
