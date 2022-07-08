package org.jetbrains.plugins.scala.lang.resolve2

class OverloadingHardTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "overloading/hardOverloadings/"
  }

  def testIgnoreValue(): Unit = doTest()
  def testImplicitsPriority(): Unit = doTest()
  def testNoOverloading(): Unit = doTest()
  def testValueFunction1(): Unit = doTest()
  def testValueFunction2(): Unit = doTest()
  def testValueFunction3(): Unit = doTest()
  def testValueFunction4(): Unit = doTest()
  def testValueFunction5(): Unit = doTest()
  def testValueFunction6(): Unit = doTest()
  def testValueFunction7(): Unit = doTest()
  def testValueFunction8(): Unit = doTest()
  def testValueFunction9(): Unit = doTest()
  //TODO
//  def testValueFunction10 = doTest
  def testFunctionObject(): Unit = doTest()
  def testFunctionObject1(): Unit = doTest()
  def testFunctionObject2(): Unit = doTest()
  def testParameterlessFunction(): Unit = doTest()
  def testParameterlessFunction2(): Unit = doTest()
}