package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaVersion

import java.nio.file.Path

class ScStringLiteralAnnotatorTest_Scala3 extends ScStringLiteralAnnotatorTestBase {
  override protected def relativeTestDataPath: Path = Path.of("annotator", "string_literals", "scala3")

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3
}
