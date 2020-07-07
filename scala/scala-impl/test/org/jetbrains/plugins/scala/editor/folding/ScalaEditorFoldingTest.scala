package org.jetbrains.plugins.scala.editor.folding

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.folding.ScalaFoldingBuilder
import org.jetbrains.plugins.scala.util.MultilineStringUtil

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * User: Dmitry.Naydanov
 * Date: 14.08.15.
 */
class ScalaEditorFoldingTest extends ScalaLightCodeInsightFixtureTestAdapter {
  private val FOLD_START_MARKER_BEGIN = "<|fold["
  private val FOLD_START_MARKER_END = "]>"
  private val FOLD_END_MARKER = "</fold>"

  private[this] def ST(text: String) =
    FOLD_START_MARKER_BEGIN + text + FOLD_START_MARKER_END
  private[this] val END = FOLD_END_MARKER

  private def genericCheckRegions(fileText: String): Unit = {
    val expectedRegions = new ArrayBuffer[(TextRange, String)]()
    val textWithoutMarkers = new StringBuilder(fileText.length)
    val openMarkers = mutable.Stack[(Int, String)]()

    var pos = 0
    while ({
      val nextStartMarker = fileText.indexOf(FOLD_START_MARKER_BEGIN, pos)
      val nextEndMarker = fileText.indexOf(FOLD_END_MARKER, pos)

      Seq(nextStartMarker -> true, nextEndMarker -> false)
        .filter(_._1 >= 0)
        .sortBy(_._1) match {
        case (closestIdx, isStartMarker) +: _ =>
          textWithoutMarkers.append(fileText.substring(pos, closestIdx))
          val idxInTargetFile = textWithoutMarkers.length

          if (isStartMarker) {
            val replacementTextBeginIdx = closestIdx + FOLD_START_MARKER_BEGIN.length
            val replacementTextEndIdx = fileText.indexOf(FOLD_START_MARKER_END, replacementTextBeginIdx)
            assert(replacementTextEndIdx >= 0, "Expected end of start marker " + replacementTextBeginIdx)
            val replacementText = fileText.substring(replacementTextBeginIdx, replacementTextEndIdx)
            pos = replacementTextEndIdx + FOLD_START_MARKER_END.length

            openMarkers.push(idxInTargetFile -> replacementText)
          } else {
            assert(openMarkers.nonEmpty, "No more open markers for end marker at " + closestIdx)
            val (regionBegin, replacementText) = openMarkers.pop()
            val regionEnd = idxInTargetFile
            pos = closestIdx + FOLD_END_MARKER.length
            expectedRegions += (TextRange.create(regionBegin, regionEnd) -> replacementText)
          }
          true
        case Seq() =>
          false
      }
    }) ()

    assert(openMarkers.isEmpty, s"Unbalanced fold markers #3: ${openMarkers.mkString}")

    val assumedRegionRanges = expectedRegions.result().sortBy(_._1.getStartOffset)

    myFixture.configureByText("dummy.scala", textWithoutMarkers.result())

    val myBuilder = new ScalaFoldingBuilder
    val regions = myBuilder.buildFoldRegions(myFixture.getFile.getNode, myFixture getDocument myFixture.getFile)

    assert(regions.length == assumedRegionRanges.size, s"Different region count, expected: ${assumedRegionRanges.size}, but got: ${regions.length}")

    (regions zip assumedRegionRanges).zipWithIndex foreach {
      case ((region, (assumedRange, expectedPlaceholderText)), idx) =>
        assert(region.getRange.getStartOffset == assumedRange.getStartOffset,
          s"Different start offsets in region #$idx : expected ${assumedRange.getStartOffset}, but got ${region.getRange.getStartOffset}")
        assert(region.getRange.getEndOffset == assumedRange.getEndOffset,
          s"Different end offsets in region #$idx : expected ${assumedRange.getEndOffset}, but got ${region.getRange.getEndOffset}")
        val actualReplacementText = myBuilder.getLanguagePlaceholderText(region.getElement, region.getRange)
        assert(actualReplacementText == expectedPlaceholderText,
          s"Different placeholderTexts in region #$idx: expected ${expectedPlaceholderText}, but got ${actualReplacementText}")
    }
  }

  val BLOCK_ST = ST("{...}")
  val PAR_ST = ST("(...)")
  val DOTS_ST = ST("...")
  val COMMENT_ST = ST("/.../")
  val DOC_COMMENT_ST = ST("/**...*/")
  val MLS_ST = ST("\"\"\"...\"\"\"")

  def testNested(): Unit = {
    val text =
      s""" class A $BLOCK_ST{
        |  1 match $BLOCK_ST{
        |    case 1 => $BLOCK_ST{
        |      //azaza
        |    }$END
        |  }$END
        |
        |  object Azazaible $BLOCK_ST{
        |    for (i <- 1 to 10) $BLOCK_ST{
        |      println("azaza!")
        |    }$END
        |  }$END
        |
        |  def boo() $BLOCK_ST{
        |    if (true) $BLOCK_ST{
        |      //azaza
        |    }$END
        |  }$END
        | }$END
      """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testMatchBody(): Unit = {
    val text =
      s"""
         | 1 match $BLOCK_ST{
         |   case 1 =>
         | }$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testClassBody(): Unit = {
    val text =
      s"""
         | class A $BLOCK_ST{
         |   //azaza
         | }$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testMethodBody(): Unit = {
    val text =
      s"""
         | def boo() $BLOCK_ST{
         |
         | }$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testIfBody(): Unit = {
    val text =
      s"""
         | if (true) $BLOCK_ST{
         |   println("")
         | }$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testMatchInner(): Unit = {
    val text =
      s"""
         |1 match $BLOCK_ST{
         |    case 1 => $BLOCK_ST{
         |
         |    }$END
         |  }$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testLambdaArgs(): Unit = {
    val text =
      s"""
         | def foo(i: Int => Int, j: Int) = i(j)
         |
         | foo$PAR_ST(
         |   jj => jj + 1, 123
         | )$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testSelectorImport(): Unit = {
    val text =
      s"""
         |  import ${DOTS_ST}scala.collection.mutable.{
         |    AbstractSeq, ArrayOps, Buffer
         |  }$END
         |
         |  class A $BLOCK_ST{
         |
         |  }$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testBlockComment(): Unit = {
    val text =
      s"""
         |  $COMMENT_ST/*
         |   * Marker trait
         |   */$END
         |  trait MyMarker
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testDocComment(): Unit = {
    val text =
      s"""
         |  $DOC_COMMENT_ST/**
         |   * Marker trait
         |   */$END
         |  trait MyMarker
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testMlString(): Unit = {
    val text =
      s"""
         | val tratata =
         |   $MLS_ST${MultilineStringUtil.MultilineQuotes}
         |     aaaaaa
         |     aaaaaa
         |     aaaaaa
         |     aaaaaa
         |   ${MultilineStringUtil.MultilineQuotes}$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }
}
