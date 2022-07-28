package org.jetbrains.plugins.scala.lang.resolve2

class InheritanceOverrideTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "inheritance/override/"
  }

  //TODO answer?
//  def testCaseClass = doTest
  def testClass(): Unit = doTest()
  def testClassParameter(): Unit = doTest()
  def testClassParameterValue(): Unit = doTest()
  //TODO classparameter
//  def testClassParameterValueFrom = doTest
  def testClassParameterValueTo(): Unit = doTest()
  def testClassParameterVariable(): Unit = doTest()
  //TODO classparameter
//  def testClassParameterVariableFrom = doTest
  def testClassParameterVariableTo(): Unit = doTest()
  def testFunction(): Unit = doTest()
  //TODO answer?
//  def testObject = doTest
  def testTrait(): Unit = doTest()
  def testValue(): Unit = doTest()
  def testVariable(): Unit = doTest()
}