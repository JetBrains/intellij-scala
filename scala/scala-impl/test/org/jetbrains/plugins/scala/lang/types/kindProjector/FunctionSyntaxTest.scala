package org.jetbrains.plugins.scala.lang.types.kindProjector

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class FunctionSyntaxTest extends KindProjectorTestBase {
  override def folderPath: Path = super.folderPath / "functionSyntax"

  def testFunctionSyntaxBounds(): Unit        = doTest()
  def testFunctionSyntaxDotTypeBounds(): Unit = doTest()
  def testHigherKind(): Unit                  = doTest()
  def testHigherKindParameterized(): Unit     = doTest()
  def testHigherKindWithVariance(): Unit      = doTest()
  def testSimple(): Unit                      = doTest()
  def testVarianceBackticks(): Unit           = doTest()
  def testVarianceSquareBrackets(): Unit      = doTest()
}
