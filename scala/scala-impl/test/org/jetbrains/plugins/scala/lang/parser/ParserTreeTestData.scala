package org.jetbrains.plugins.scala.lang.parser

import java.nio.file.{Files, Path}

/**
 * Holds ordinary Scala source without test-error markers and its complete expected PSI tree.
 */
final case class ParserTreeTestData(
  source: String,
  expectedTree: String
)

object ParserTreeTestData {
  /**
   * Reads the .scala source and .tree expectation without parsing or asserting.
   */
  def load(testDataDirectory: Path, testDataName: String): ParserTreeTestData = {
    val source = Files.readString(
      testDataDirectory.resolve(s"$testDataName.scala")
    )
    val expectedTree = Files.readString(
      testDataDirectory.resolve(s"$testDataName.tree")
    )
    ParserTreeTestData(source, expectedTree)
  }
}
