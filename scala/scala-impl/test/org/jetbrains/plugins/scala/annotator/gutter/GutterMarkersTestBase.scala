package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.codeInsight.daemon.LineMarkerInfo.LineMarkerGutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.TextRangeExt
import org.jetbrains.plugins.scala.{ScalaBundle, TypecheckerTests}
import org.junit.Assert
import org.junit.Assert.{assertEquals, assertTrue, fail}
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters.ListHasAsScala

@Category(Array(classOf[TypecheckerTests]))
abstract class GutterMarkersTestBase extends ScalaFixtureTestCase {

  protected def doTestNoLineMarkersAtCaret(fileText: String): Unit =
    doTest(fileText) {
      val gutters = myFixture.findGuttersAtCaret().asScala.toSeq
      assertNoGutters(gutters, s"no gutters expected at caret, but got:")
    }

  protected def doTestNoLineMarkers(fileText: String, fileExtension: String = "scala"): Unit =
    doTest(fileText, fileExtension) {
      val gutters = myFixture.findAllGutters().asScala.toSeq
      assertNoGutters(gutters, "no gutters expected, but got:")
    }

  private def assertNoGutters(gutters: Seq[GutterMark], baseMessage: String): Unit = {
    if (gutters.nonEmpty) {
      val guttersText = guttersDebugText(gutters)
      assertEquals(baseMessage, "", guttersText)
    }
  }

  protected def refToElement(superClass: String, superMethod: String, refText: String): String =
    s"""<a href="#element/$superClass#$superMethod"><code>$refText</code></a>"""

  protected def refToClass(className: String): String =
    s"""<a href="#element/$className"><code>$className</code></a>"""

  protected def recursionTooltip(methodName: String, isTailRecursive: Boolean) =
    s"Method '$methodName' is ${if (isTailRecursive) "tail recursive" else "recursive"}"

  protected def recursiveCallTooltip: String = ScalaBundle.message("call.is.recursive")

  // TODO: why using callback? we could just call it "prepareFile" and call as usual
  protected def doTest(fileText: String, fileExtension: String = "scala")(testFn: => Any): Unit = {
    val name = getTestName(false)
    myFixture.configureByText(s"$name.$fileExtension", StringUtil.convertLineSeparators(fileText, "\n"))
    myFixture.doHighlighting()
    testFn
  }

  protected def doTestSingleTooltipAtCaret(fileText: String, expectedTooltipParts: String*): Unit = {
    assertTrue("Tooltip text expected", expectedTooltipParts.nonEmpty)

    doTest(fileText) {
      val gutters = myFixture.findGuttersAtCaret().asScala.toSeq
      gutters match {
        case Seq(marker) =>
          val actualTooltip = marker.getTooltipText
          val missingPart = expectedTooltipParts.find(!actualTooltip.contains(_))
          missingPart.foreach { missing =>
            assertEquals(s"Gutter mark must include `$missing`", missing, actualTooltip)
          }
        case Seq() =>
          fail("Gutter mark expected at caret, but no markers found")
        case markers =>
          fail(s"Expected single gutter at caret, but got:\n${guttersDebugText(markers)}")
      }
    }
  }

  protected def doTestAllTooltipsAtCaret(fileText: String, expectedTooltips: String*): Unit = {
    assertTrue("Tooltips expected", expectedTooltips.nonEmpty)

    doTest(fileText) {
      val markers = myFixture.findGuttersAtCaret().asScala.toSeq
      val actualTooltips = markers.map(_.getTooltipText)

      val errorMsg =
        if (markers.isEmpty)
          s"Expected ${expectedTooltips.length} ${StringUtil.pluralize("marker", expectedTooltips.length)} at caret, but no markers found"
        else
          s"Expected tooltips do not match actual markers:\n${guttersDebugText(markers)}"

      Assert.assertEquals(errorMsg, actualTooltips.toList.sorted, expectedTooltips.toList.sorted)
    }
  }

  protected def doTestAllGuttersShort(fileText: String, expectedGutters: Seq[ExpectedGutter], fileExtension: String = "scala"): Unit = {
    val expectedGuttersSortedText = guttersDebugText(expectedGutters.sorted)
    doTestAllGuttersShortWithText(
      fileText,
      expectedGuttersSortedText,
      fileExtension
    )
  }

  protected def doTestAllGuttersShortWithText(fileText: String, expectedGuttersSortedText: String, fileExtension: String = "scala"): Unit =
    doTest(fileText, fileExtension) {
      val gutters0 = myFixture.findAllGutters().asScala.toSeq
      val gutters = gutters0.map(toFullExpectedGutter)
      val guttersShort = gutters.map(g => g.copy(tooltipContent = extractFirstParagraph(g.tooltipContent).getOrElse(g.tooltipContent)))

      val guttersSorted = guttersShort.sorted
      val actualText = guttersDebugText(guttersSorted)
      assertEquals(expectedGuttersSortedText, actualText)
    }

  /** Use this if gutter html content is too complex to directly test it via assertEquals */
  protected def doTestAllGuttersParts(fileText: String, expectedGutters: Seq[ExpectedGutterParts], fileExtension: String = "scala"): Unit =
    doTest(fileText, fileExtension) {
      val gutters0 = myFixture.findAllGutters().asScala.toSeq
      val gutters = gutters0.map(toFullExpectedGutter)

      val guttersSorted = gutters.sorted
      val expectedGuttersSorted = expectedGutters.sorted

      // Used to show in diff view only
      val expectedPartsText = "ONLY TOOLTIP PARTS\n" + guttersDebugText(expectedGuttersSorted)
      val actualText = "FULL TOOLTIP CONTENT\n" +guttersDebugText(guttersSorted)
      //assertEquals(expectedPartsText, actualText) // don't directly compare cause

      val zipped: Seq[(ExpectedGutterParts, ExpectedGutter)] = expectedGuttersSorted.zipAll(guttersSorted, null, null)
      zipped.foreach {
        case (null, actual) =>
          fail(s"Unexpected gutter found: ${gutterDebugText(actual)}")
        case (expected, null)  =>
          fail(s"Expected gutter not found: ${gutterDebugText(expected)}")
        case (expected, actual)  =>
          // Yes, we compare line and ranges, but if they are not equal we show a more convenient diff view with all gutters debug text
          if (expected.line != actual.line)
            assertEquals(s"Couldn't find gutter at line ${expected.line}", expectedPartsText, actualText)
          // TODO: support testing of multiple gutters on the same line
          if (expected.range != actual.range)
            assertEquals(s"Gutter range at line ${expected.line} is different", expectedPartsText, actualText)

          val missingPart = expected.tooltipParts.find(!actual.tooltipContent.contains(_))
          missingPart.foreach { missing =>
            assertEquals(s"Gutter at line ${expected.line} must include `$missing`", gutterDebugText(expected), gutterDebugText(actual))
          }
      }
    }

  private def toFullExpectedGutter(gutter: GutterMark): ExpectedGutter =
    toExpectedGutter(gutter, identity)

  private def toExpectedGutter(gutter: GutterMark, tooltipContentDecorator: String => String): ExpectedGutter = {
    val info = gutter.asInstanceOf[LineMarkerGutterIconRenderer[_]].getLineMarkerInfo
    val line = myFixture.getDocument(myFixture.getFile).getLineNumber(info.startOffset) + 1
    val tooltip = info.getLineMarkerTooltip
    val tooltipDecorated = tooltipContentDecorator(tooltip)
    ExpectedGutter(line, TextRange.create(info.startOffset, info.endOffset), tooltipDecorated)
  }

  implicit def PositionalOrdering[T <: WithRange]: Ordering[T] =
    Ordering.by[T, Tuple2[Int, Int]](_.range.toTuple)

  trait WithRange {
    def range: TextRange
  }

  /** @param line 1-based */
  case class ExpectedGutter(line: Int, range: TextRange, tooltipContent: String) extends WithRange
  object ExpectedGutter {
    def apply(line: Int, range: (Int, Int), tooltipContent: String): ExpectedGutter =
      new ExpectedGutter(line, TextRange.create(range._1, range._2), tooltipContent)
  }

  /** @param line 1-based */
  case class ExpectedGutterParts(line: Int, range: TextRange, tooltipParts: Seq[String])  extends WithRange
  object ExpectedGutterParts {
    def apply(line: Int, range: (Int, Int), tooltipParts: String*): ExpectedGutterParts =
      new ExpectedGutterParts(line, TextRange.create(range._1, range._2), tooltipParts)
  }

  private def extractFirstParagraph(tooltipHtml: String): Option[String] = {
    val htmlSingleLine = tooltipHtml.trim.replace("\r", "").replace("\n", "")
    val regex = "<html><body><p>(.*?)</p>.*?</body></html>".r
    regex.findFirstMatchIn(htmlSingleLine).map(_.group(1))
  }

  private def guttersDebugText(gutters: Seq[GutterMark]): String =
    gutters.map(gutterDebugText).mkString("\n")

  private def guttersDebugText(gutters: Seq[ExpectedGutter])
                              (implicit d: DummyImplicit): String =
    gutters.map(gutterDebugText).mkString("\n")

  private def guttersDebugText(gutters: Seq[ExpectedGutterParts])
                              (implicit d1: DummyImplicit, d2: DummyImplicit): String =
    gutters.map(gutterDebugText).mkString("\n")

  private def gutterDebugText(gutter: GutterMark): String =
    gutter match {
      case renderer: LineMarkerGutterIconRenderer[_] =>
        gutterDebugText(renderer)
      case _ =>
        gutter.getTooltipText
    }

  private def gutterDebugText(gutter: LineMarkerGutterIconRenderer[_]): String =
    gutterDebugText(toFullExpectedGutter(gutter))

  private def gutterDebugText(gutter: ExpectedGutter): String = {
    val start = gutter.range.getStartOffset
    val end = gutter.range.getEndOffset
    val line = gutter.line
    s"line $line ($start, $end) ${gutter.tooltipContent}"
  }

  private def gutterDebugText(gutter: ExpectedGutterParts): String = {
    val start = gutter.range.getStartOffset
    val end = gutter.range.getEndOffset
    val line = gutter.line
    s"line $line ($start, $end) ${gutter.tooltipParts}"
  }
}
