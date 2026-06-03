package org.jetbrains.plugins.scala
package lang
package navigation

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.junit.Assert.assertTrue

class GoToImplementationTest extends GoToTestBase {

  private def doTest(text: String): Unit = {
    val (textWithoutMarkers, expectedRanges) =
      MarkersUtils.extractMarker(text.withNormalizedSeparator.trim, START, END, caretMarker = Some(CARET))
    configureFromFileText(textWithoutMarkers)

    val gotoData = CodeInsightTestUtil.gotoImplementation(getEditor, getFile)
    val targets = gotoData.targets.toSeq
    val actualRanges = targets.map(_.getTextRange)

    val expectedRangesNotFound = expectedRanges.filterNot(actualRanges.contains)

    assertTrue(
      s"Targets not found for source: ${gotoData.source.getText}",
      actualRanges.nonEmpty)
    assertTrue(
      s"""Targets found at:
         |${rangesDebugText(actualRanges, textWithoutMarkers)}
         |not found:
         |${rangesDebugText(expectedRangesNotFound, textWithoutMarkers)}""".stripMargin,
      expectedRangesNotFound.isEmpty)

    assertTrue(
      s"""Found too many targets:
         |${rangesDebugText(actualRanges, textWithoutMarkers)}
         |expected:
         |${rangesDebugText(expectedRanges, textWithoutMarkers)}""".stripMargin,
      actualRanges.lengthIs == expectedRanges.length)
  }

  private def rangesDebugText(ranges: Seq[TextRange], fileText: String): String = {
    val rangeTexts = ranges.map(rangeDebugText(_, fileText))
    rangeTexts.mkString("  ", "\n  ", "")
  }

  private def rangeDebugText(range: TextRange, fileText: String): String = {
    val rangeText = fileText.substring(range.getStartOffset, range.getEndOffset)
    s"$range[$rangeText]"
  }

  def testTraitImplementation(): Unit = doTest(
    s"""
       |trait a {
       |  def f$CARET
       |}
       |trait b extends a {
       |  ${START}def f = 1$END
       |}
       |case class c(${START}override val f: Int = 2$END) extends b
       |case class d() extends b
      """.stripMargin
  )

  def testTraitImplementation2(): Unit = doTest(
    s"""
       |trait a {
       |  def f$CARET = 0
       |}
       |trait b extends a {
       |  ${START}def f = 1$END
       |}
       |case class c(${START}override val f: Int = 2$END) extends b
       |case class d() extends b
      """.stripMargin
  )

  def testAbstractClassImplementation(): Unit = doTest(
    s"""
       |abstract class a {
       |  def f$CARET
       |}
       |trait b extends a {
       |  ${START}def f = 1$END
       |}
       |case class c(${START}override val f: Int = 2$END) extends b
       |case class d() extends b
      """.stripMargin
  )

  def testAbstractClassImplementation2(): Unit = doTest(
    s"""
       |abstract class a {
       |  def f$CARET = 0
       |}
       |trait b extends a {
       |  ${START}def f = 1$END
       |}
       |case class c(${START}override val f: Int = 2$END) extends b
       |case class d() extends b
      """.stripMargin
  )

  def testAbstractOverride(): Unit = doTest(
    s"""
       |abstract class Writer {
       |  def pri${CARET}nt(str: String)
       |}
       |
       |class ConsoleWriter extends Writer {
       |  ${START}def print(str: String) = println(str)$END
       |}
       |
       |trait Uppercase extends Writer {
       |  ${START}abstract override def print(str: String) =
       |    super.print(str.toUpperCase())$END
       |}
       |
       |object Test {
       |  val writer = new ConsoleWriter with Uppercase
       |  writer.print("abc")
       |}
       |""".stripMargin
  )

  def testOverrideWithoutImplementationFromCall(): Unit = doTest(
    s"""
       |trait a {
       |  def f
       |}
       |
       |trait b extends a {
       |  override def f: Int
       |}
       |
       |class c extends b {
       |  ${START}override def f = 1$END
       |}
       |
       |object test {
       |  val x = new c()
       |  println(x.${CARET}f)
       |}
      """.stripMargin
  )

  def testAbstractMethodInTraitFromCall(): Unit = doTest(
    s"""
       |trait DeleteMe {
       |    def hello(): Unit
       |}
       |
       |class DeleteMeImpl extends DeleteMe {
       |    ${START}override def hello(): Unit = println("Hello")$END
       |}
       |
       |object DeleteMe {
       |    def main(args: Array[String]): Unit = {
       |        val deleteMe: DeleteMe = new DeleteMeImpl
       |        deleteMe.hel${CARET}lo()
       |    }
       |}
      """.stripMargin
  )

  def testMethodInTraitFromCall(): Unit = doTest(
    s"""
       |trait DeleteMe {
       |    ${START}def hello(): Unit = {}$END
       |}
       |
       |class DeleteMeImpl extends DeleteMe {
       |    ${START}override def hello(): Unit = println("Hello")$END
       |}
       |
       |object DeleteMe {
       |    def main(args: Array[String]): Unit = {
       |        val deleteMe: DeleteMe = new DeleteMeImpl
       |        deleteMe.hel${CARET}lo()
       |    }
       |}
      """.stripMargin
  )

  def testAbstractMethodInAbstractClassFromCall(): Unit = doTest(
    s"""
       |abstract class DeleteMe {
       |    def hello(): Unit
       |}
       |
       |class DeleteMeImpl extends DeleteMe {
       |    ${START}override def hello(): Unit = println("Hello")$END
       |}
       |
       |object DeleteMe {
       |    def main(args: Array[String]): Unit = {
       |        val deleteMe: DeleteMe = new DeleteMeImpl
       |        deleteMe.hel${CARET}lo()
       |    }
       |}
      """.stripMargin
  )

  def testMethodInAbstractClassFromCall(): Unit = doTest(
    s"""
       |abstract class DeleteMe {
       |    ${START}def hello(): Unit = {}$END
       |}
       |
       |class DeleteMeImpl extends DeleteMe {
       |    ${START}override def hello(): Unit = println("Hello")$END
       |}
       |
       |object DeleteMe {
       |    def main(args: Array[String]): Unit = {
       |        val deleteMe: DeleteMe = new DeleteMeImpl
       |        deleteMe.hel${CARET}lo()
       |    }
       |}
      """.stripMargin
  )
}
