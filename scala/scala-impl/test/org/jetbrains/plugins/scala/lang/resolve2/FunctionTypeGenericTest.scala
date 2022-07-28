package org.jetbrains.plugins.scala.lang.resolve2

class FunctionTypeGenericTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/type/generic/"
  }

  def testFunction1(): Unit = doTest()
  def testFunction2(): Unit = doTest()
  //TODO answer?
//  def testFunctionExpression1 = doTest
  def testFunctionExpression2(): Unit = doTest()
  //TODO answer?
//  def testGeneric1 = doTest
  def testGeneric2(): Unit = doTest()
}