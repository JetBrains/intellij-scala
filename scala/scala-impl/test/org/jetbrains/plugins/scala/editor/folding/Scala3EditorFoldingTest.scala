package org.jetbrains.plugins.scala.editor.folding
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3EditorFoldingTest extends ScalaEditorFoldingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  val BODY_ST = ST(":...")
  val INDENT_EXPR_ST = ST(" ...")

  def testBracelessClassBody(): Unit = genericCheckRegions(
    s"""
       |class Test$BODY_ST:
       |  def test = 0
       |  def test2 = 1$END
       |""".stripMargin
  )

  def testBracelessExpressionBlock(): Unit = genericCheckRegions(
    s"""
       |def test =${INDENT_EXPR_ST}
       |  println("test")
       |  println("test 2")$END
       |""".stripMargin
  )

  def testBracelessExpressionBlockWithComment(): Unit = genericCheckRegions(
    s"""
       |def test =${INDENT_EXPR_ST}
       |  // a comment
       |  println("test")
       |  println("test 2")$END
       |""".stripMargin
  )

  def testMatch(): Unit = genericCheckRegions(
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

  def testMatchType(): Unit = genericCheckRegions(
    s"""type Widen[Tup <: Tuple] <: Tuple =
       |  Tup match$INDENT_EXPR_ST
       |    case EmptyTuple => EmptyTuple
       |    case EmptyTuple => EmptyTuple
       |    case h *: t => h *: t$END
       |""".stripMargin
  )

  def testMatchType_WithBraces(): Unit = genericCheckRegions(
    s"""type Widen[Tup <: Tuple] <: Tuple =
       |  Tup match $BLOCK_ST{
       |    case EmptyTuple => EmptyTuple
       |    case EmptyTuple => EmptyTuple
       |    case h *: t => h *: t
       |  }$END
       |""".stripMargin
  )
}
