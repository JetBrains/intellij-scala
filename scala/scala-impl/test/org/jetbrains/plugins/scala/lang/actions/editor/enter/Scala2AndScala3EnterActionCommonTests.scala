package org.jetbrains.plugins.scala.lang.actions.editor.enter

import junit.framework.Test
import org.jetbrains.plugins.scala.base.EditorActionTestBase

trait Scala2AndScala3EnterActionCommonTests extends Test {
  self: EditorActionTestBase =>

  def testNoIndentAfterReturn_UnitType(): Unit = {
    checkGeneratedTextAfterEnter(
      s"""def foo: Unit = {
         |  return$CARET
         |}""".stripMargin,
      s"""def foo: Unit = {
         |  return
         |  $CARET
         |}""".stripMargin
    )
    checkGeneratedTextAfterEnter(
      s"""def foo: scala.Unit = {
         |  return$CARET
         |}""".stripMargin,
      s"""def foo: scala.Unit = {
         |  return
         |  $CARET
         |}""".stripMargin
    )
    checkGeneratedTextAfterEnter(
      s"""def foo: _root_.scala.Unit = {
         |  return$CARET
         |}""".stripMargin,
      s"""def foo: _root_.scala.Unit = {
         |  return
         |  $CARET
         |}""".stripMargin
    )
  }

  def testIndentAfterReturn_NonUnitType(): Unit = checkGeneratedTextAfterEnter(
    s"""def foo: Int = {
       |  return$CARET
       |}""".stripMargin,
    s"""def foo: Int = {
       |  return
       |    $CARET
       |}""".stripMargin
  )

  def testIndentAfterReturn_NoType(): Unit = checkGeneratedTextAfterEnter(
    s"""def foo = {
       |  return$CARET
       |}""".stripMargin,
    s"""def foo = {
       |  return
       |    $CARET
       |}""".stripMargin
  )
}
