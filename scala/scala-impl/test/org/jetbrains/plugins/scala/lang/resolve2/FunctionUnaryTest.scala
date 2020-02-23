package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionUnaryTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/unary/"
  }

  def testParenthesisedPrefix(): Unit = doTest()
  def testUnary(): Unit = doTest()
  def testUnaryIllegal(): Unit = doTest()
  def testUnaryParameter(): Unit = doTest()
}