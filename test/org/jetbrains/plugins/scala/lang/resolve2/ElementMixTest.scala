package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class ElementMixTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "element/mix/"
  }

  def testCaseClassAndClass = doTest
  def testCaseClassAndObject = doTest
  def testCaseClassAndTrait = doTest
  def testCaseClassAndTypeAlias = doTest
  def testFunctionAndClass = doTest
  //TODO object also can be applicable! do not filter object
//  def testFunctionAndObject = doTest
  def testFunctionAndTrait = doTest
  def testFunctionAndTypeAlias = doTest
  //TODO classparameter
//  def testClassParameterAndFunction = doTest
  def testClassParameterAndValue = doTest
  def testClassParameterAndVariable = doTest
  def testFunctionParameterAndObject = doTest
  def testFunctionParameterAndValue1 = doTest
  def testFunctionParameterAndValue2 = doTest
  def testFunctionParameterAndVariable = doTest
  def testFunctionTypeParameterAndClass = doTest
  def testFunctionTypeParameterAndTrait = doTest
  def testFunctionTypeParameterAndValue = doTest
  def testClassAndObject = doTest
  def testClassAndTrait = doTest
  def testClassAndTypeAlias = doTest
  def testObjectAndTrait = doTest
  def testObjectAndTypeAlias = doTest
  def testTraitAndTypeAlias = doTest

}