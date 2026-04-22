package org.jetbrains.plugins.scala.lang.parser.scala3

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.base.{DefaultFileSetTestTransform, NoSdkFileSetTestBase}

import java.nio.file.Path

/**
 * @see [[org.jetbrains.plugins.scala.lang.parser.ScalaParserTest]]
 */
class Scala3ParserTest extends NoSdkFileSetTestBase with DefaultFileSetTestTransform {
  override protected def relativeTestDataPath: Path = Path.of("parser", "data3")

  override def language: Language = Scala3Language.INSTANCE
}
