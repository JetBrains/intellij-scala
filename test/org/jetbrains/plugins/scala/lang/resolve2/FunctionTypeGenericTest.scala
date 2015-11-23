package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionTypeGenericTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/type/generic/"
  }

  def testFunction1() = doTest()
  def testFunction2() = doTest()
  //TODO answer?
//  def testFunctionExpression1 = doTest
  def testFunctionExpression2() = doTest()
  //TODO answer?
//  def testGeneric1 = doTest
  def testGeneric2() = doTest()
}