package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class InheritanceThisElementTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "inheritance/this/element/"
  }

  //TODO
//  def testBlock = doTest
  def testClass = doTest
  //TODO
  //def testClassParameter = doTest
  def testClassParameterValue = doTest
  def testClassParameterVariable = doTest
  //TODO
//  def testFile = doTest
  //TODO
//  def testFunction = doTest
  def testObject = doTest
  def testTrait = doTest
}