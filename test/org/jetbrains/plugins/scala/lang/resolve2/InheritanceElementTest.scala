package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class InheritanceElementTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "inheritance/element/"
  }

  def testCaseClass = doTest
  def testCaseObject = doTest
  def testClass = doTest
  def testClassParameter = doTest
  def testClassParameterValue = doTest
  def testClassParameterVariable = doTest
  def testClassTypeParameter = doTest
  def testFunction = doTest
  def testImport = doTest
  def testObject = doTest
  def testTrait = doTest
  def testTypeAlias = doTest
  def testValue = doTest
  def testVariable = doTest
}