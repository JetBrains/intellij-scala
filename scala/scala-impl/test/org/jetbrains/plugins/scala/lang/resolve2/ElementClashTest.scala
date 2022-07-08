package org.jetbrains.plugins.scala.lang.resolve2

class ElementClashTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "element/clash/"
  }
  //TODO classes clash
//  def testClass = doTest
  //TODO classes clash
//  def testCaseClass = doTest
  def testClassParameter(): Unit = doTest()
  def testClassParameterValue(): Unit = doTest()
  def testClassParameterVariable(): Unit = doTest()
  def testCaseObject(): Unit = doTest()
  def testCaseClauseBinding(): Unit = doTest()
  def testCaseClauseNamed(): Unit = doTest()
  //TODO classes clash
//  def testTrait = doTest
  //TODO
//  def testNamedParameter = doTest
  def testObject(): Unit = doTest()
  def testFunctionDefinition(): Unit = doTest()
  def testFunctionExpressionParameter(): Unit = doTest()
  def testFunctionParameter(): Unit = doTest()
  def testFunctionParameterClause(): Unit = doTest()
  def testConstructorParameter(): Unit = doTest()
  def testStatementForAssignment(): Unit = doTest()
  def testStatementForBinding(): Unit = doTest()
  def testStatementForValues(): Unit = doTest()
  def testTypeAlias(): Unit = doTest()
  def testValue(): Unit = doTest()
  def testVariable(): Unit = doTest()
  def testTypeParameterClass(): Unit = doTest()
  def testTypeParameterFunction(): Unit = doTest()
  def testTypeParameterTrait(): Unit = doTest()
}