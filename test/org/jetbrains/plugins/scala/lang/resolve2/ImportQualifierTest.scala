package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportQualifierTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/qualifier/"
  }

  def testImport1 = doTest
  def testImport2 = doTest
  def testValue1 = doTest
  def testValue2 = doTest
  //TODO
//  def testValueCaseClass = doTest
  def testValueClass = doTest
  def testVariable = doTest
  def testFunction = doTest
}