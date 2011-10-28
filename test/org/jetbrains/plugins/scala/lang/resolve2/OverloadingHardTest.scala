package org.jetbrains.plugins.scala.lang.resolve2

/**
 * @author Alexander Podkhalyuzin
 */

class OverloadingHardTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "overloading/hardOverloadings/"
  }

  def testIgnoreValue = doTest
  def testImplicitsPriority = doTest
  def testNoOverloading = doTest
  def testValueFunction1 = doTest
  def testValueFunction2 = doTest
  def testValueFunction3 = doTest
  def testValueFunction4 = doTest
  def testValueFunction5 = doTest
  def testValueFunction6 = doTest
  def testValueFunction7 = doTest
  def testValueFunction8 = doTest
  def testValueFunction9 = doTest
  //TODO
//  def testValueFunction10 = doTest
  def testFunctionObject = doTest
  def testFunctionObject1 = doTest
  def testFunctionObject2 = doTest
  def testParameterlessFunction = doTest
  def testParameterlessFunction2 = doTest
}