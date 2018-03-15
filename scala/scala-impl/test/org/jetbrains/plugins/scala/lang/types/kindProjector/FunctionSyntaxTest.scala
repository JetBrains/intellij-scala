package org.jetbrains.plugins.scala.lang.types.kindProjector

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 7/1/15
 */
class FunctionSyntaxTest extends KindProjectorTestBase {
  override def folderPath = super.folderPath + "functionSyntax/"

  def testFunctionSyntaxBounds() = doTest()

  def testFunctionSyntaxDotTypeBounds() = doTest()

  def testHigherKind() = doTest()

  def testHigherKindParameterized() = doTest()

  def testHigherKindWithVariance() = doTest()

  def testSimple() = doTest()

  def testVarianceBackticks() = doTest()

  def testVarianceSquareBrackets() = doTest()
}
