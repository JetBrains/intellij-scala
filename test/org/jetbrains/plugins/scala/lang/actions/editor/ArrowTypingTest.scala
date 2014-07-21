package org.jetbrains.plugins.scala
package lang.actions.editor

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

/**
 * User: Dmitry.Naydanov
 * Date: 10.07.14.
 */
class ArrowTypingTest extends ScalaLightCodeInsightFixtureTestAdapter {
  private var settings: ScalaCodeStyleSettings = null

  override protected def setUp(): Unit = {
    super.setUp()

    settings = ScalaCodeStyleSettings.getInstance(myFixture.getProject)
  }

  def testSimpleCase() {
    val before =
      s"""
        |object A {
        |  123 match { case$CARET_MARKER }
        |}
      """.stripMargin

    val after = s"""
        |object A {
        |  123 match { case $CARET_MARKER => }
        |}
      """.stripMargin

    checkGeneratedTextAfterTyping(before, after, ' ')
  }

  def testFunction() {
    val before =
      s"""
         |list.filter {
         |  case$CARET_MARKER
         |}
       """.stripMargin

    val after =
      s"""
         |list.filter {
         |  case $CARET_MARKER =>
         |}
       """.stripMargin

    checkGeneratedTextAfterTyping(before, after, ' ')
  }

  def testDontTouchArrow1() {
    val before =
      s"""
         |123 match {
         |  case 321 $CARET_MARKER=>
         |}
       """.stripMargin

    val after =
      s"""
         |123 match {
         |  case 321 =$CARET_MARKER>
         |}
       """.stripMargin

    checkGeneratedTextAfterTyping(before, after, '=')
  }

  def testDontTouchArrow2() {
    val before =
      s"""
         |123 match {
         |  case 321 =$CARET_MARKER>
         |}
       """.stripMargin

    val after =
      s"""
         |123 match {
         |  case 321 =>$CARET_MARKER
         |}
       """.stripMargin

    checkGeneratedTextAfterTyping(before, after, '>')
  }

  def testDontTouchSpace() {
    val before =
      s"""
         |123 match {
         |  case 321$CARET_MARKER =>
         |}
       """.stripMargin

    val after =
      s"""
         |123 match {
         |  case 321 $CARET_MARKER=>
         |}
       """.stripMargin

    checkGeneratedTextAfterTyping(before, after, ' ')
  }

  def testReplaceCaseArrow() {
    settings.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR = true

    val before =
      s"""
         | 123 match {
         |  case 321 =$CARET_MARKER
         | }
       """.stripMargin

    val after =
      s"""
         | 123 match {
         |  case 321 ${ScalaTypedHandler.unicodeCaseArrow}$CARET_MARKER
         | }
       """.stripMargin

    checkGeneratedTextAfterTyping(before, after, '>')
  }

  def testReplaceFunTypeArrow() {
    settings.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR = true

    val before =
      s"""
         |val b: Int =$CARET_MARKER
       """.stripMargin

    val after =
      s"""
         |val b: Int ${ScalaTypedHandler.unicodeCaseArrow}$CARET_MARKER
       """.stripMargin

    checkGeneratedTextAfterTyping(before, after, '>')
  }


  def testReplaceMapArrow() {
    settings.REPLACE_MAP_ARROW_WITH_UNICODE_CHAR = true

    val before =
      s"""
         |val map = Map(a -$CARET_MARKER)
       """.stripMargin

    val after =
      s"""
         |val map = Map(a ${ScalaTypedHandler.unicodeMapArrow}$CARET_MARKER)
       """.stripMargin

    checkGeneratedTextAfterTyping(before, after, '>')
  }

  def testReplaceForGeneratorArrow() {
    settings.REPLACE_FOR_GENERATOR_ARROW_WITH_UNICODE_CHAR = true

    val before =
      s"""
         | for (j <$CARET_MARKER )
       """.stripMargin

    val after =
      s"""
         | for (j ${ScalaTypedHandler.unicodeForGeneratorArrow}$CARET_MARKER )
       """.stripMargin

    checkGeneratedTextAfterTyping(before, after, '-')
  }
}
