package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionRepeatTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/repeat/"
  }

  def testArraya() = doTest()
  def testArrayRaw() = doTest()
  def testEmpty() = doTest()
  def testNone() = doTest()
  def testOne() = doTest()
  def testTwo() = doTest()
  def testIncompatibleArraya() = doTest()
  def testIncompatibleOne() = doTest()
  def testIncompatibleTwo() = doTest()
}