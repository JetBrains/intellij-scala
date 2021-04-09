package org.jetbrains.plugins.scala.editor.folding

import org.junit.Assert.assertTrue

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

  def testMatchInner_WithBraces(): Unit = {
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

  def testMatchInner(): Unit = {
    val text =
      s"""
         |1 match $BLOCK_ST{
         |  case 1 =>
         |    ${DOTS_ST}println(1)
         |    println(2)$END
         |}$END
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
         |  import $DOTS_ST${COLLAPSED_BY_DEFAULT_MARKER}scala.collection.mutable.{
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

  def testBlockCommentAsFileHeader(): Unit = runWithModifiedFoldingSettings { settings =>
    def codeExample(isCollapsedByDefault: Boolean): String = {
      val collapsedMarker = if (isCollapsedByDefault) COLLAPSED_BY_DEFAULT_MARKER else ""
      s"""$COMMENT_ST$collapsedMarker/*
         | * Some comment in the beginning of the file
         | */$END
         |
         |class A""".stripMargin
    }

    assertTrue(settings.COLLAPSE_FILE_HEADER)
    genericCheckRegions(codeExample(true))

    settings.COLLAPSE_FILE_HEADER = false
    genericCheckRegions(codeExample(false))
  }

  def testMlString(): Unit = {
    val text =
      s"""
         |$MLS_ST\"\"\"
         |   1
         |   2
         |   3
         |\"\"\"$END
         |""".stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testMlString_WithStripMargin(): Unit = {
    val text =
      s"""
         |$MLS_ST\"\"\"
         |  |1
         |  |2
         |  |3
         |  |\"\"\"$END.stripMargin
         |""".stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  def testMlString_WithStripMargin_AndTrim(): Unit = {
    val text =
      s"""
         |$MLS_ST\"\"\"
         |  |1
         |  |2
         |  |3
         |  |\"\"\"$END.stripMargin.trim
         |""".stripMargin.replace("\r", "")

    genericCheckRegions(text)
  }

  // TODO: currently, two overlapping foldings are added:
  //  1. for multiline definition body
  //  2. for multiline string literal
  //  It's a little bit inconvenient because "Collapse all foldings" action hides placeholder for multiline string """..."""
  //  Looks like in theory in such cases we could avoid adding folding for the definition body
  //  if the body "looks" as single-line when multiline string is collapsed
  //  \
  //  For example, for given method:
  //    def foo =
  //      """1
  //        |2
  //        |3""".stripMargin
  //  we would like to see this placeholder:
  //    def foo =
  //      """...""".stripMargin
  //  instead of:
  //    def foo =
  //      ...
  def testMlString_AsDefinitionBody(): Unit = {
    val text =
      s"""val test =
         | $DOTS_ST$MLS_ST\"\"\"
         |   1
         |   2
         |   3
         | \"\"\"$END$END
         |
         |def test =
         | $DOTS_ST$MLS_ST\"\"\"
         |   1
         |   2
         |   3
         | \"\"\"$END$END
       """.stripMargin.replace("\r", "")

    genericCheckRegions(text, sortFoldings = true)
  }

  def testMlString_AsDefinitionBody_WithStripMargin(): Unit = {
    val text =
      s"""val test =
         | $DOTS_ST$MLS_ST\"\"\"
         |    |1
         |    |2
         |    |3
         |    |\"\"\"$END.stripMargin$END
         |
         |def test =
         |  $DOTS_ST$MLS_ST\"\"\"
         |    |1
         |    |2
         |    |3
         |    |\"\"\"$END.stripMargin$END
         |""".stripMargin.replace("\r", "")

    genericCheckRegions(text, sortFoldings = true)
  }

  def testMlString_AsDefinitionBody_WithStripMargin_AndTrim(): Unit = {
    val text =
      s"""val test =
         |  $DOTS_ST$MLS_ST\"\"\"
         |    |1
         |    |2
         |    |3
         |    |\"\"\"$END.stripMargin.trim$END
         |
         |def test =
         |  $DOTS_ST$MLS_ST\"\"\"
         |    |1
         |    |2
         |    |3
         |    |\"\"\"$END.stripMargin.trim$END
         |""".stripMargin.replace("\r", "")

    genericCheckRegions(text, sortFoldings = true)
  }

  // SCL-3464
  def testCodeBlock_SCL_3464(): Unit = genericCheckRegions(
    s""""foo" should $BLOCK_ST{
       |  "return bar" in $BLOCK_ST{
       |    // some code
       |  }$END
       |  "return baz" in $BLOCK_ST{
       |    // some other code
       |  }$END
       |}$END
       |""".stripMargin)

  //SCL-4686
  def testFor(): Unit = genericCheckRegions(
    s"""for {
       |  x <- 1 to 2
       |  y <- 1 to 2
       |} yield $BLOCK_ST{
       |  println(1)
       |  println(2)
       |}$END""".stripMargin
  )

  def testTypeAlias(): Unit = genericCheckRegions(
    s"""type T = ${DOTS_ST}Tuple3[
      |  String,
      |  Long,
      |  Int
      |]$END
      |""".stripMargin
  )

  def testTypeAlias_Incomplete(): Unit = genericCheckRegions(
    """/** Type of the tail of a tuple */
      |type Tail[X <: AnyRef] <: AnyRef =
      |""".stripMargin
  )
}
