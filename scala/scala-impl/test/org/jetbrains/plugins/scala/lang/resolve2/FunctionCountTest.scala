package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionCountTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/count/"
  }

  def testEmptyToEmpty() = doTest()
  def testEmptyToNone() = doTest()
  def testEmptyToOne() = doTest()
  def testNoneToEmpty() = doTest()
  def testNoneToNone() = doTest()
  def testNoneToOne() = doTest()
  def testOneToEmpty() = doTest()
  def testOneToNone() = doTest()
  def testOneToOne() = doTest()
  def testOneToTwo() = doTest()
  def testTwoToOne() = doTest()
  def testTwoToTwo() = doTest()
  def testTupling() = doTest()
}