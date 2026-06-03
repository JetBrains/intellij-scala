package org.jetbrains.plugins.scala
package lang
package parser

import org.jetbrains.plugins.scala.base.{DefaultFileSetTestTransform, NoSdkFileSetTestBase}

import java.nio.file.Path

class FailedParserTest extends NoSdkFileSetTestBase with DefaultFileSetTestTransform {
  override protected def relativeTestDataPath: Path = Path.of("parser", "failed")

  override protected def shouldPass: Boolean = false
}
