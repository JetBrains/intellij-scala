package org.jetbrains.plugins.scala.editor.folding
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert.{assertFalse, fail}

import scala.util.{Failure, Try}

class Scala3EditorFoldingTest extends ScalaEditorFoldingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  protected val INDENT_REGION_WITH_COLON = ST(":...")
  protected val INDENT_REGION            = ST(" ...")
  protected val INDENT_EXPR_ST           = ST(" ...")

  // `isFoldingForAllBlocks` setting should not affect the behaviour of block foldings
  // that are already folded without this setting
  private def testShouldNotBeAffectedByFoldingForAllBlocksSetting(testBody: () => Unit): Unit =
    runWithModifiedScalaFoldingSettings { settings =>
      assertFalse(settings.isFoldingForAllBlocks)
      testBody()

      settings.setFoldingForAllBlocks(true)
      try testBody() catch {
        case ex: Throwable =>
          System.err.println("!!!! FoldingForAllBlocks enabled !!!!")
          throw ex
      }
    }

  override def genericCheckRegions(fileTextRaw: String, sortFoldings: Boolean): Unit =
    testShouldNotBeAffectedByFoldingForAllBlocksSetting {
      () => super.genericCheckRegions(fileTextRaw, sortFoldings)
    }

  def testTemplateBody_IndentationRegion(): Unit = {
    val Start = INDENT_REGION_WITH_COLON
    genericCheckRegions(
      s"""class Test$Start:
         |  def test = 0
         |  def test2 = 1$END
         |
         |trait Test$Start:
         |  def test = 0
         |  def test2 = 1$END
         |
         |object Test$Start:
         |  def test = 0
         |  def test2 = 1$END
         |
         |enum Test$Start:
         |  case A
         |  case B$END
         |
         |new C $Start:
         |  println("test")
         |  println("test")$END
         |""".stripMargin
    )
  }

  def testTemplateBody_IndentationRegion_WithEndMarker(): Unit = {
    val Start = INDENT_REGION_WITH_COLON
    genericCheckRegions(
      s"""class Test$Start:
         |  def test = 0
         |  def test2 = 1
         |end Test$END
         |
         |trait Test$Start:
         |  def test = 0
         |  def test2 = 1
         |end Test$END
         |
         |object Test$Start:
         |  def test = 0
         |  def test2 = 1
         |end Test$END
         |
         |enum Test$Start:
         |  case A
         |  case B
         |end Test$END
         |
         |new C $Start:
         |  println("test")
         |  println("test")
         |end new$END
         |""".stripMargin
    )
  }

  def testExtension_IndentationRegion(): Unit = genericCheckRegions(
    s"""extension (x: String)$INDENT_REGION
       |  def f1: String = x.toString + "_1"
       |
       |  def f2: String = x.toString + "_2"$END
       |""".stripMargin
  )

  def testExtension_IndentationRegion_WithEndMarker(): Unit = genericCheckRegions(
    s"""extension (x: String)$INDENT_REGION
       |  def f1: String = x.toString + "_1"
       |
       |  def f2: String = x.toString + "_2"
       |end extension$END
       |""".stripMargin
  )

  def testDefWithAssignmentBody_IndentationRegion(): Unit = runWithModifiedScalaFoldingSettings { settings =>
    def doTest(startMarker: String): Unit = {
      genericCheckRegions(
        s"""def foo =$startMarker
           |  println("test")
           |  ???$END
           |
           |def foo =$startMarker
           |  // line comment in the beginning of hte body
           |  println("test")
           |  ???$END
           |
           |var foo =$startMarker
           |  println("test")
           |  ???$END
           |
           |val foo =$startMarker
           |  println("test")
           |  ???$END
           |
           |given foo[A]: StringParser[Option[A]] =$startMarker
           |  println("test")
           |  ???$END
           |
           |class A $BLOCK_ST{
           |  def this(i: Int) =$startMarker
           |    this()
           |    println("test")$END
           |}$END
           |""".stripMargin
      )

    }

    assertFalse(settings.isCollapseDefinitionWithAssignmentBodies)
    doTest(INDENT_EXPR_ST)

    settings.setCollapseDefinitionWithAssignmentBodies(true)
    doTest(INDENT_EXPR_ST + COLLAPSED_BY_DEFAULT_MARKER)

    // just checking that the test correctly tests...
    Try(doTest(INDENT_EXPR_ST)) match {
      case Failure(_: AssertionError) => // expected
      case _ =>
        fail("the test should fail with an assertion error")
    }
  }

  def testDefWithAssignmentBody_WithBraces(): Unit = {
    val Start = BLOCK_ST
    genericCheckRegions(
      s"""def foo = $Start{
         |  println("test")
         |  ???
         |}$END
         |
         |def foo = $Start{
         |  // line comment in the beginning of the body
         |  println("test")
         |  ???
         |}$END
         |
         |var foo = $Start{
         |  println("test")
         |  ???
         |}$END
         |
         |val foo = $Start{
         |  println("test")
         |  ???
         |}$END
         |
         |given foo[A]: StringParser[Option[A]] = $Start{
         |  println("test")
         |  ???
         |}$END
         |
         |class A $BLOCK_ST{
         |  def this(i: Int) = $Start{
         |    this()
         |    println("test")
         |  }$END
         |}$END""".stripMargin
    )
  }

  //SCL-4465
  def testDefWithAssignmentBody_SingleMultilineExpression_1(): Unit = {
    val Start = DOTS_ST
    genericCheckRegions(
      s"""def foo = ${Start}1 + 1 +
         |  1 + 1 +
         |  1 + 1$END
         |
         |def foo = ${Start}1 + 1 +
         |  1 + 1 +
         |  1 + 1$END
         |
         |var foo = ${Start}1 + 1 +
         |  1 + 1 +
         |  1 + 1$END
         |
         |val foo = ${Start}1 + 1 +
         |  1 + 1 +
         |  1 + 1$END
         |
         |given foo[A]: StringParser[Option[A]] = ${Start}1 + 1 +
         |  1 + 1 +
         |  1 + 1$END
         |
         |class A(x: Int) $BLOCK_ST{
         |  def this(i: String) = ${Start}this$PAR_ST(1 + 1 +
         |    1 + 1 +
         |    1 + 1)$END$END
         |}$END
         |""".stripMargin
    )
  }

  def testDefWithAssignmentBody_SingleMultilineExpression_2(): Unit = {
    val Start = DOTS_ST
    genericCheckRegions(
      s"""def foo = ${Start}println$PAR_ST(
         |  "test"
         |)$END$END
         |
         |def foo = ${Start}println$PAR_ST(
         |  "test"
         |)$END$END
         |
         |var foo = ${Start}println$PAR_ST(
         |  "test"
         |)$END$END
         |
         |val foo = ${Start}println$PAR_ST(
         |  "test"
         |)$END$END
         |
         |given foo[A]: StringParser[Option[A]] = ${Start}println$PAR_ST(
         |  "test"
         |)$END$END
         |""".stripMargin
    )
  }

  def testDefWithAssignmentBody_SingleMultilineExpression_3(): Unit = {
    val Start = DOTS_ST
    genericCheckRegions(
      s"""def foo = ${Start}Seq(1, 2)
         |  .filter(_ => true)
         |  .map(x => x * 2)$END
         |
         |def foo = ${Start}Seq(1, 2)
         |  .filter(_ => true)
         |  .map(x => x * 2)$END
         |
         |var foo = ${Start}Seq(1, 2)
         |  .filter(_ => true)
         |  .map(x => x * 2)$END
         |
         |val foo = ${Start}Seq(1, 2)
         |  .filter(_ => true)
         |  .map(x => x * 2)$END
         |
         |given foo[A]: StringParser[Option[A]] = ${Start}Seq(1, 2)
         |  .filter(_ => true)
         |  .map(x => x * 2)$END
         |
         |class A(x: Seq[Int]) $BLOCK_ST{
         |  def this(i: String) = ${Start}this$PAR_ST(
         |    Seq(1, 2)
         |      .filter(_ => true)
         |      .map(x => x * 2)
         |  )$END$END
         |}$END
         |""".stripMargin
    )
  }

  def testDefWithAssignmentBody_SingleMultilineExpression_4_WithEndMarker(): Unit = {
    val Start = DOTS_ST
    genericCheckRegions(
      s"""def foo = ${Start}Seq(1, 2)
         |  .filter(_ => true)
         |  .map(x => x * 2)
         |end foo$END
         |
         |def foo = ${Start}Seq(1, 2)
         |  .filter(_ => true)
         |  .map(x => x * 2)
         |end foo$END
         |
         |var foo = ${Start}Seq(1, 2)
         |  .filter(_ => true)
         |  .map(x => x * 2)
         |end foo$END
         |
         |val foo = ${Start}Seq(1, 2)
         |  .filter(_ => true)
         |  .map(x => x * 2)
         |end foo$END
         |
         |given foo[A]: StringParser[Option[A]] = ${Start}Seq(1, 2)
         |  .filter(_ => true)
         |  .map(x => x * 2)
         |end foo$END
         |""".stripMargin
    )
  }

  def testIndentationRegion_DefWithAssignmentBody_WithEndMarker(): Unit = {
    val Start = INDENT_EXPR_ST
    genericCheckRegions(
      s"""def test =$Start
         |  println("test")
         |  ???
         |end test$END
         |
         |var test =$Start
         |  println("test")
         |  ???
         |end test$END
         |
         |val test =$Start
         |  println("test")
         |  ???
         |end test$END
         |
         |val (test1, test2) =$Start
         |  println("test")
         |  ???
         |end val$END
         |
         |given test[A]: StringParser[Option[A]] =$Start
         |  println("test")
         |  ???
         |end test$END
         |
         |given test[A]: StringParser[Option[A]] =$Start
         |  println("test")
         |  ???
         |end given$END
         |
         |class A $BLOCK_ST{
         |  def this(i: Int) =$Start
         |    this()
         |    println("test")
         |  end this$END
         |}$END
         |""".stripMargin
    )
  }

  def testMatch_IndentationRegion(): Unit = genericCheckRegions(
    s"""1 match$INDENT_EXPR_ST
       |  case 1 =>
       |  case 2 =>
       |  case 3 =>
       |  case 4 =>$END
       |""".stripMargin
  )

  def testMatch_WithBraces(): Unit = genericCheckRegions(
    s"""1 match $BLOCK_ST{
       |  case 1 =>
       |  case 2 =>
       |  case 3 =>
       |  case 4 =>
       |}$END
       |""".stripMargin
  )

  def testMatchType_IndentationRegion(): Unit = genericCheckRegions(
    s"""type Widen[Tup <: Tuple] <: Tuple =
       |  ${DOTS_ST}Tup match$INDENT_EXPR_ST
       |    case EmptyTuple => EmptyTuple
       |    case EmptyTuple => EmptyTuple
       |    case h *: t => h *: t$END$END
       |""".stripMargin
  )

  def testMatchType_WithBraces(): Unit = genericCheckRegions(
    s"""type Widen[Tup <: Tuple] <: Tuple =
       |  ${DOTS_ST}Tup match $BLOCK_ST{
       |    case EmptyTuple => EmptyTuple
       |    case EmptyTuple => EmptyTuple
       |    case h *: t => h *: t
       |  }$END$END
       |""".stripMargin
  )

  def testIfThenElse_IndentationRegion(): Unit = genericCheckRegions(
    s"""if true then$INDENT_EXPR_ST
       |  println("test")
       |  println("test")$END
       |else$INDENT_EXPR_ST
       |  println("test")
       |  println("test")$END""".stripMargin
  )

  def testIfThenElse_IndentationRegion_WithEndMarker(): Unit = genericCheckRegions(
    s"""if true then$INDENT_EXPR_ST
       |  println("test")
       |  println("test")
       |end if$END
       |
       |if true then$INDENT_EXPR_ST
       |  println("test")
       |  println("test")$END
       |else$INDENT_EXPR_ST
       |  println("test")
       |  println("test")
       |end if$END""".stripMargin
  )

  def testIfThenElse_WithBraces_ElseOnSameLineWithIfBlock(): Unit = genericCheckRegions(
    s"""if (true) then $BLOCK_ST{
       |  println("test")
       |  println("test")
       |}$END else $BLOCK_ST{
       |  println("test")
       |  println("test")
       |}$END
       |
       |if (true) $BLOCK_ST{
       |  println("test")
       |  println("test")
       |}$END else $BLOCK_ST{
       |  println("test")
       |  println("test")
       |}$END
       |""".stripMargin
  )

  def testIfThenElse_WithBraces_ElseOnNewLine(): Unit = genericCheckRegions(
    s"""if (true) then $BLOCK_ST{
       |  println("test")
       |  println("test")
       |}$END
       |else $BLOCK_ST{
       |  println("test")
       |  println("test")
       |}$END
       |
       |if (true) $BLOCK_ST{
       |  println("test")
       |  println("test")
       |}$END
       |else $BLOCK_ST{
       |  println("test")
       |  println("test")
       |}$END
       |""".stripMargin
  )

  def testPackage_IndentationRegion(): Unit = genericCheckRegions(
    s"""package a.b.c$INDENT_REGION_WITH_COLON:
       |  class A
       |  class B$END
       |""".stripMargin
  )

  def testPackage_IndentationRegion_WithEndMarker(): Unit = genericCheckRegions(
    s"""package a.b.c$INDENT_REGION_WITH_COLON:
       |  class A
       |  class B
       |end с$END
       |""".stripMargin
  )

  def testPackage_WithBraces(): Unit = genericCheckRegions(
    s"""package a.b.c $BLOCK_ST{
       |  class A
       |  class B
       |}$END
       |""".stripMargin
  )

  def testTopLevelFunctionAsFirstFileElement(): Unit = runWithModifiedScalaFoldingSettings { settings =>
    def codeExample(startMarker: String): String = {
      s"""@main
         |def Main(args: String*): Unit =$startMarker
         |  println("1")
         |  println("2")
         |end Main$END
         |
         |private def runExample(name: String)(f: => Unit): Unit =$startMarker
         |  println(Console.MAGENTA + s"$$name example:" + Console.RESET)
         |  f
         |  println()$END
         |""".stripMargin
    }

    assertFalse(settings.isCollapseDefinitionWithAssignmentBodies)
    genericCheckRegions(codeExample(INDENT_EXPR_ST))

    settings.setCollapseDefinitionWithAssignmentBodies(true)
    genericCheckRegions(codeExample(INDENT_EXPR_ST + COLLAPSED_BY_DEFAULT_MARKER))
  }
}
