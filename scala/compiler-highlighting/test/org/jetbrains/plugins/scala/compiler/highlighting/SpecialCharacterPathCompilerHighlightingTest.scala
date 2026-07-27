package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaVersion

class SpecialCharacterPathCompilerHighlightingTest extends ScalaCompilerHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  def testPlus(): Unit = {
    runTestCase(
      fileName = "direc+tory/Person.scala",
      content =
        """case class Person(name: String, age: Int):
          |  def greeting: Int = s"Hello, $name!"
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(65, 81)),
          quickFixDescriptions = Seq.empty,
          msgPrefix = "Found:    String"
        )
      )
    )
  }

  def testSpace(): Unit = {
    runTestCase(
      fileName = "two words/Person.scala",
      content =
        """case class Person(name: String, age: Int):
          |  def greeting: Int = s"Hello, $name!"
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(65, 81)),
          quickFixDescriptions = Seq.empty,
          msgPrefix = "Found:    String"
        )
      )
    )
  }

  def testChevron(): Unit = {
    runTestCase(
      fileName = "Chevron‹Person›.scala",
      content =
        """case class Person(name: String, age: Int):
          |  def greeting: Int = s"Hello, $name!"
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(65, 81)),
          quickFixDescriptions = Seq.empty,
          msgPrefix = "Found:    String"
        )
      )
    )
  }
}
