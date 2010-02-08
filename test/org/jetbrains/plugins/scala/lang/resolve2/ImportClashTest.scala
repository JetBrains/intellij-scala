package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportClashTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/clash/"
  }

  def testFunction1 = doTest
  def testFunction2 = doTest
  def testFunction3 = doTest
  def testType1 = doTest
  def testType2 = doTest
  def testType3 = doTest
  def testTypeAndValue1 = doTest
  def testTypeAndValue2 = doTest
  def testTypeAndValue3 = doTest
  def testTypeAndValueAll = doTest
  def testValue1 = doTest
  def testValue2 = doTest
  def testValue3 = doTest
}