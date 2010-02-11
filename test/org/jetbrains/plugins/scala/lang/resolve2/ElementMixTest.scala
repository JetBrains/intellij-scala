package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class ElementMixTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "element/mix/"
  }

  //TODO
//  def testCaseClassAndClass = doTest
  def testCaseClassAndObject = doTest
  //TODO
//  def testCaseClassAndTrait = doTest
  //TODO
//  def testCaseClassAndTypeAlias = doTest

  def testFunctionAndClass = doTest
  //TODO
//  def testFunctionAndObject = doTest
  def testFunctionAndTrait = doTest
  def testFunctionAndTypeAlias = doTest
  
  def testFunctionParameterAndObject = doTest
  def testFunctionParameterAndValue1 = doTest
  def testFunctionParameterAndValue2 = doTest
  def testFunctionParameterAndVariable = doTest
  def testFunctionTypeParameterAndClass = doTest
  def testFunctionTypeParameterAndTrait = doTest
  def testFunctionTypeParameterAndValue = doTest

  def testClassAndObject = doTest
  //TODO
//  def testClassAndTrait = doTest
  //TODO
//  def testClassAndTypeAlias = doTest
  def testObjectAndTrait = doTest
  def testObjectAndTypeAlias = doTest
  //TODO
//  def testTraitAndTypeAlias = doTest

}