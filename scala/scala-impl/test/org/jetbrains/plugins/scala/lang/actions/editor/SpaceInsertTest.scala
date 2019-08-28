package org.jetbrains.plugins.scala.lang.actions.editor

import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

class SpaceInsertTest extends EditorActionTestBase {

  private def doTest(before: String, after: String): Unit = {
    checkGeneratedTextAfterTyping(before, after, ' ')
  }

  def testIfElse(): Unit = {
    getScalaSettings.ALIGN_IF_ELSE = true
    val before =
      s"""def test = {
         |  val x = if (true) 8
         |  else$CARET
         |}""".stripMargin
    val after =
      s"""def test = {
         |  val x = if (true) 8
         |          else $CARET
         |}""".stripMargin
    doTest(before, after)
  }

  def testMatchCase(): Unit = {
    val before =
      s"""val x = 5 match {
         |  case 1 => 2
         |    case$CARET
         |}""".stripMargin
    val after =
      s"""val x = 5 match {
         |  case 1 => 2
         |  case $CARET
         |}""".stripMargin
    doTest(before, after)
  }
}
