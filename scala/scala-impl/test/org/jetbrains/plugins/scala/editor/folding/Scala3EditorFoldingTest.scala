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
}
