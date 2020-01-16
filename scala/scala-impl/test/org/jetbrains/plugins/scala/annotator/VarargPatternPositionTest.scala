package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase

class VarargPatternPositionTest extends ScalaAnnotatorQuickFixTestBase {

  override protected val description = "'_*' can be used only for last argument"

  def testLastPosition(): Unit =
    checkTextHasNoErrors(
      """val List(x, y@ _*) = List()"""
    )

  def testNonLastPosition(): Unit =
    checkTextHasError(
      s"""val List(x, y@ ${START}_*$END, z) = List()"""
    )

  def testNonLastPosition_1(): Unit =
    checkTextHasError(
      s"""val List(y@ ${START}_*$END, z) = List()"""
    )

  def testNonLastPositionShortVararg(): Unit =
    checkTextHasError(
      s"""val List(x, ${START}_*$END, z) = List()"""
    )

  def testNonLastPositionShortVararg_1(): Unit =
    checkTextHasError(
      s"""val List(${START}_*$END, z) = List()"""
    )
}
