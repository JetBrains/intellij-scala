package org.jetbrains.plugins.scala.lang.types.kindProjector

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 7/1/15
 */
class FunctionSyntaxTest extends KindProjectorTestBase {
  override def folderPath = super.folderPath + "functionSyntax/"

  def testFunctionSyntaxBounds(): Unit        = doTest()
  def testFunctionSyntaxDotTypeBounds(): Unit = doTest()
  def testHigherKind(): Unit                  = doTest()
  def testHigherKindParameterized(): Unit     = doTest()
  def testHigherKindWithVariance(): Unit      = doTest()
  def testSimple(): Unit                      = doTest()
  def testVarianceBackticks(): Unit           = doTest()
  def testVarianceSquareBrackets(): Unit      = doTest()
}
