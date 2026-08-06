package org.jetbrains.plugins.scala.lang.formatter.intellij.tests

import org.jetbrains.plugins.scala.lang.formatter.intellij.FormatterTestBase

import java.nio.file.Path

class ScalaDocFormatterTest extends FormatterTestBase {
  override protected def relativeTestDataPath: Path = Path.of("formatter", "scalaDocData")
}
