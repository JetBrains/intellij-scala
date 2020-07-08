package org.jetbrains.plugins.scala.editor.folding

import org.jetbrains.plugins.scala.util.MultilineStringUtil

/**
 * User: Dmitry.Naydanov
 * Date: 14.08.15.
 */
class ScalaEditorFoldingTest extends ScalaEditorFoldingTestBase {
  def testNested(): Unit = {
    val text =
      s""" class A $BLOCK_ST{
        |  1 match $BLOCK_ST{
        |    case 1 => $DOTS_ST{
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
         |    case 1 => $DOTS_ST{
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
