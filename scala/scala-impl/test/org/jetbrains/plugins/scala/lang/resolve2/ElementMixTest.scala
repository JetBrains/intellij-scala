package org.jetbrains.plugins.scala.lang.resolve2

class ElementMixTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "element/mix/"
  }
  //TODO classes clash
//  def testCaseClassAndClass = doTest
  def testCaseClassAndObject(): Unit = doTest()
  //TODO classes clash
//  def testCaseClassAndTrait = doTest
  def testCaseClassAndTypeAlias(): Unit = doTest()
  def testFunctionAndClass(): Unit = doTest()
  //TODO object also can be applicable! do not filter object
//  def testFunctionAndObject = doTest
  def testFunctionAndTrait(): Unit = doTest()
  def testFunctionAndTypeAlias(): Unit = doTest()
  //TODO classparameter
//  def testClassParameterAndFunction = doTest
  def testClassParameterAndValue(): Unit = doTest()
  def testClassParameterAndVariable(): Unit = doTest()
  def testFunctionParameterAndObject(): Unit = doTest()
  def testFunctionParameterAndValue1(): Unit = doTest()
  def testFunctionParameterAndValue2(): Unit = doTest()
  def testFunctionParameterAndVariable(): Unit = doTest()
  def testFunctionTypeParameterAndClass(): Unit = doTest()
  def testFunctionTypeParameterAndTrait(): Unit = doTest()
  def testFunctionTypeParameterAndValue(): Unit = doTest()
  def testClassAndObject(): Unit = doTest()
  //TODO classes clash
//  def testClassAndTrait = doTest
  def testClassAndTypeAlias(): Unit = doTest()
  def testObjectAndTrait(): Unit = doTest()
  def testObjectAndTypeAlias(): Unit = doTest()
  def testTraitAndTypeAlias(): Unit = doTest()

}