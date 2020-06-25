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
  private val FOLD_START_MARKER = "<|fold>"
  private val FOLD_END_MARKER = "</fold>"
  private val FOLD_MARKER_LENGTH = FOLD_START_MARKER.length

  private[this] val ST = FOLD_START_MARKER
  private[this] val END = FOLD_END_MARKER

  private def genericCheckRegions(fileText: String): Unit = {
    val myRegions = new ArrayBuffer[TextRange]()
    val myFileText = new StringBuilder(fileText.length)
    val myOpenMarkers = mutable.Stack[Int]()

    var i1 = fileText indexOf FOLD_START_MARKER
    var i2 = fileText indexOf FOLD_END_MARKER

    var overallFixOffset = 0
    @inline def increaseOverall(): Unit = overallFixOffset += FOLD_MARKER_LENGTH
    @inline def appendPair(errorPlaceMsg: String): Unit = {
      assert(myOpenMarkers.nonEmpty, "Unbalanced fold markers " + errorPlaceMsg)
      val st = myOpenMarkers.pop()
      myRegions += new TextRange(st, i2 - overallFixOffset)
    }

    assert(i1 > -1 && i2 > -2, s"Bad fold markers: $i1 and $i2")

    myFileText append fileText.substring(0, i1)

    while (i1 > -1 || i2 > -1) {
      if (i2 < i1 && i2 > -1) {
        appendPair("#1")

        val i2Old = i2
        i2 = fileText.indexOf(FOLD_END_MARKER, i2Old + 1)
        myFileText append fileText.substring(i2Old + FOLD_MARKER_LENGTH, if (i2 > 0) Math.min(i2, i1) else i1)

        increaseOverall()
      } else if (i1 < i2 && i1 > -1) {
        myOpenMarkers.push(i1 - overallFixOffset)

        increaseOverall()

        val i1Old = i1
        i1 = fileText.indexOf(FOLD_START_MARKER, i1Old + 1)

        myFileText append fileText.substring(i1Old + FOLD_MARKER_LENGTH, if (i1 > -1) Math.min(i2, i1) else i2)
      } else if (i1 < i2) { //i1 == -1
        appendPair("#1.5")

        increaseOverall()

        val i2Old = i2
        i2 = fileText.indexOf(FOLD_END_MARKER, i2Old + 1)
        myFileText.append (
          if (i2 == -1) fileText.substring(i2Old + FOLD_MARKER_LENGTH) else fileText.substring(i2Old + FOLD_MARKER_LENGTH, i2)
        )
      } else assert(assertion = false, "Unbalanced fold markers #2")
    }

    assert(myOpenMarkers.isEmpty, s"Unbalanced fold markers #3: ${myOpenMarkers.mkString}")

    val assumedRegionRanges = myRegions.result().sortWith((x, y) => x.getStartOffset < y.getStartOffset)

    myFixture.configureByText("dummy.scala", myFileText.result())

    val myBuilder = new ScalaFoldingBuilder
    val regions = myBuilder.buildFoldRegions(myFixture.getFile.getNode, myFixture getDocument myFixture.getFile)

    assert(regions.length == assumedRegionRanges.size, s"Different region count, expected: ${assumedRegionRanges.size}, but got: ${regions.length}")

    (regions zip assumedRegionRanges).zipWithIndex foreach {
      case ((region, assumedRange), idx) =>
        assert(region.getRange.getStartOffset == assumedRange.getStartOffset,
          s"Different start offsets in region #$idx : expected ${assumedRange.getStartOffset}, but got ${region.getRange.getStartOffset}")
        assert(region.getRange.getEndOffset == assumedRange.getEndOffset,
          s"Different end offsets in region #$idx : expected ${assumedRange.getEndOffset}, but got ${region.getRange.getEndOffset}")
    }
  }

  def testNested(): Unit = {
    val text =
      s""" class A $ST{
        |  1 match $ST{
        |    case 1 => $ST{
        |      //azaza
        |    }$END
        |  }$END
        |
        |  object Azazaible $ST{
        |    for (i <- 1 to 10) $ST{
        |      println("azaza!")
        |    }$END
        |  }$END
        |
        |  def boo() $ST{
        |    if (true) $ST{
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
         | 1 match $ST{
         |   case 1 =>
         | }$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testClassBody(): Unit = {
    val text =
      s"""
         | class A $ST{
         |   //azaza
         | }$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testMethodBody(): Unit = {
    val text =
      s"""
         | def boo() $ST{
         |
         | }$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testIfBody(): Unit = {
    val text =
      s"""
         | if (true) $ST{
         |   println("")
         | }$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testMatchInner(): Unit = {
    val text =
      s"""
         |1 match $ST{
         |    case 1 => $ST{
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
         | foo$ST(
         |   jj => jj + 1, 123
         | )$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testSelectorImport(): Unit = {
    val text =
      s"""
         |  import ${ST}scala.collection.mutable.{
         |    AbstractSeq, ArrayOps, Buffer
         |  }$END
         |
         |  class A $ST{
         |
         |  }$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testBlockComment(): Unit = {
    val text =
      s"""
         |  $ST/*
         |   * Marker trait
         |   */$END
         |  trait MyMarker
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testDocComment(): Unit = {
    val text =
      s"""
         |  $ST/**
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
         |   $ST${MultilineStringUtil.MultilineQuotes}
         |     aaaaaa
         |     aaaaaa
         |     aaaaaa
         |     aaaaaa
         |   ${MultilineStringUtil.MultilineQuotes}$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }
}
