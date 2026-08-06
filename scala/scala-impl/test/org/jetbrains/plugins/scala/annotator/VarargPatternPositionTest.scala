package org.jetbrains.plugins.scala.annotator

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class VarargPatternPositionTest extends ScalaAnnotatorQuickFixTestBase {

  override protected val description = "_* can be used only for last argument"

  private val allowAdditionalHighlights = true

  def testLastPosition(): Unit =
    checkTextHasNoErrors(
      """val List(x, y@ _*) = List()"""
    )

  def testNonLastPosition(): Unit =
    checkTextHasError(
      s"""val List(x, y@ ${START}_*$END, z) = List()""", allowAdditionalHighlights
    )

  def testNonLastPosition_1(): Unit =
    checkTextHasError(
      s"""val List(y@ ${START}_*$END, z) = List()""", allowAdditionalHighlights
    )

  def testNonLastPosition_ShortVararg(): Unit =
    checkTextHasError(
      s"""val List(x, ${START}_*$END, z) = List()""", allowAdditionalHighlights
    )

  def testNonLastPosition_ShortVararg_1(): Unit =
    checkTextHasError(
      s"""val List(${START}_*$END, z) = List()""", allowAdditionalHighlights
    )
}
