package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionUnaryTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/unary/"
  }

  def testParenthesisedPrefix() = doTest()
  def testUnary() = doTest()
  def testUnaryIllegal() = doTest()
  def testUnaryParameter() = doTest()
}